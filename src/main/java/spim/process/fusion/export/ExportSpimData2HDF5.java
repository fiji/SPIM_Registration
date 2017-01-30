package spim.process.fusion.export;

import ij.gui.GenericDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.resave.Generic_Resave_HDF5;
import spim.fiji.plugin.resave.Generic_Resave_HDF5.Parameters;
import spim.fiji.plugin.resave.ProgressWriterIJ;
import spim.fiji.plugin.resave.Resave_HDF5;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;

public class ExportSpimData2HDF5 implements ImgExport
{

	private List< TimePoint > newTimepoints;

	private List< ViewSetup > newViewSetups;

	private Parameters params;

	private SpimData2 spimData;

	private Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo;

	private HashMap< ViewId, Partition > viewIdToPartition;

	private final ProgressWriter progressWriter = new ProgressWriterIJ();

	@Override
	public boolean finish()
	{
		System.out.println( "finish()" );
		String path = params.getSeqFile().getAbsolutePath();
		try
		{
			new XmlIoSpimData2( "" ).save( spimData, path );

			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + path + "'." );

			// this spimdata object was not modified, we just wrote a new one
			return false;
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + path + "'." );
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public void setXMLData( final List< TimePoint > newTimepoints, final List< ViewSetup > newViewSetups )
	{
		System.out.println( "setXMLData()" );
		this.newTimepoints = newTimepoints;
		this.newViewSetups = newViewSetups;
	}

	@Override
	public boolean queryParameters( SpimData2 spimData, final boolean is16bit )
	{
		System.out.println( "queryParameters()" );

		if ( newTimepoints == null || newViewSetups == null )
		{
			IOFunctions.println( "new timepoints and new viewsetup list not set yet ... cannot continue" );
			return false;
		}

		perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( newViewSetups );

		String fn = LoadParseQueryXML.defaultXMLfilename;
		if ( fn.endsWith( ".xml" ) )
			fn = fn.substring( 0, fn.length() - ".xml".length() );
		for ( int i = 0;; ++i )
		{
			Generic_Resave_HDF5.lastExportPath = String.format( "%s-f%d.xml", fn, i );
			if ( !new File( Generic_Resave_HDF5.lastExportPath ).exists() )
				break;
		}

		final int firstviewSetupId = newViewSetups.get( 0 ).getId();
		params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true, getDescription(), is16bit );

		if ( params == null )
		{
			System.out.println( "abort " );
			return false;
		}

		Pair< SpimData2, HashMap< ViewId, Partition > > init = initSpimData( newTimepoints, newViewSetups, params, perSetupExportMipmapInfo );
		this.spimData = init.getA();
		viewIdToPartition = init.getB();

		return true;
	}

	protected static Pair< SpimData2, HashMap< ViewId, Partition > > initSpimData(
			final List< TimePoint > newTimepoints,
			final List< ViewSetup > newViewSetups,
			final Parameters params,
			final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo )
	{
		// SequenceDescription containing the subset of viewsetups and timepoints. Does not have an ImgLoader yet.
		final SequenceDescription seq = new SequenceDescription( new TimePoints( newTimepoints ), newViewSetups, null, null );

		// Create identity ViewRegistration for all views.
		final Map< ViewId, ViewRegistration > regMap = new HashMap< ViewId, ViewRegistration >();
		for ( final ViewDescription vDesc : seq.getViewDescriptions().values() )
			regMap.put( vDesc, new ViewRegistration( vDesc.getTimePointId(), vDesc.getViewSetupId() ) );
		final ViewRegistrations viewRegistrations = new ViewRegistrations( regMap );

		// Create empty ViewInterestPoints.
		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( new HashMap< ViewId, ViewInterestPointLists >() );

		// base path is directory containing the XML file.
		File basePath = params.getSeqFile().getParentFile();

		ArrayList< Partition > hdf5Partitions = null;
		HashMap< ViewId, Partition > viewIdToPartition = new HashMap< ViewId, Partition >();

		if ( params.getSplit() )
		{
			String basename = params.getHDF5File().getAbsolutePath();
			if ( basename.endsWith( ".h5" ) )
				basename = basename.substring( 0, basename.length() - ".h5".length() );
		    hdf5Partitions = Partition.split( newTimepoints, newViewSetups, params.getTimepointsPerPartition(), params.getSetupsPerPartition(), basename );
			for ( final ViewDescription vDesc : seq.getViewDescriptions().values() )
				for ( Partition p : hdf5Partitions )
					if ( p.contains( vDesc ) )
					{
						viewIdToPartition.put( vDesc, p );
						break;
					}
			WriteSequenceToHdf5.writeHdf5PartitionLinkFile( seq, perSetupExportMipmapInfo, hdf5Partitions, params.getHDF5File() );
		}
		else
		{
			final HashMap< Integer, Integer > timepointIdSequenceToPartition = new HashMap< Integer, Integer >();
			for ( final TimePoint timepoint : newTimepoints )
				timepointIdSequenceToPartition.put( timepoint.getId(), timepoint.getId() );
			final HashMap< Integer, Integer > setupIdSequenceToPartition = new HashMap< Integer, Integer >();
			for ( final ViewSetup setup : newViewSetups )
				setupIdSequenceToPartition.put( setup.getId(), setup.getId() );
			final Partition partition = new Partition( params.getHDF5File().getAbsolutePath(), timepointIdSequenceToPartition, setupIdSequenceToPartition );
			for ( final ViewDescription vDesc : seq.getViewDescriptions().values() )
				viewIdToPartition.put( vDesc, partition );
		}

		seq.setImgLoader( new Hdf5ImageLoader( params.getHDF5File(), hdf5Partitions, seq, false ) );
		SpimData2 spimData = new SpimData2( basePath, seq, viewRegistrations, viewsInterestPoints, new BoundingBoxes() );

		return new ValuePair< SpimData2, HashMap<ViewId,Partition> >( spimData, viewIdToPartition );
	}

	@Override
	public < T extends RealType< T > & NativeType< T >> boolean exportImage( RandomAccessibleInterval< T > img, BoundingBoxGUI bb, TimePoint tp, ViewSetup vs )
	{
		System.out.println( "exportImage1()" );
		return exportImage( img, bb, tp, vs, Double.NaN, Double.NaN );
	}

	public static < T extends RealType< T > > double[] updateAndGetMinMax( final RandomAccessibleInterval< T > img, final Parameters params )
	{
		double min, max;

		if ( params == null || params.getConvertChoice() == 0 || Double.isNaN( params.getMin() ) || Double.isNaN( params.getMin() ) )
		{
			final float[] minmax = FusionHelper.minMax( img );
			min = minmax[ 0 ];
			max = minmax[ 1 ];

			min = Math.max( 0, min - ((min+max)/2.0) * 0.1 );
			max = max + ((min+max)/2.0) * 0.1;

			if ( params != null )
			{
				params.setMin( min );
				params.setMax( max );
			}
		}
		else
		{
			min = params.getMin();
			max = params.getMax();
		}

		IOFunctions.println( "Min intensity for 16bit conversion: " + min );
		IOFunctions.println( "Max intensity for 16bit conversion: " + max );

		return new double[]{ min, max };
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< UnsignedShortType > convert( final RandomAccessibleInterval< T > img, final Parameters params )
	{
		final double[] minmax = updateAndGetMinMax( img, params );

		final RealUnsignedShortConverter< T > converter = new RealUnsignedShortConverter< T >( minmax[ 0 ], minmax[ 1 ] );

		return new ConvertedRandomAccessibleInterval<T, UnsignedShortType>( img, converter, new UnsignedShortType() );
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( RandomAccessibleInterval< T > img, BoundingBoxGUI bb, TimePoint tp, ViewSetup vs, double min, double max )
	{
		System.out.println( "exportImage2()" );

		// write the image
		final RandomAccessibleInterval< UnsignedShortType > ushortimg;
		if ( ! UnsignedShortType.class.isInstance( Util.getTypeFromInterval( img ) ) )
			ushortimg = convert( img, params );
		else
			ushortimg = ( RandomAccessibleInterval ) img;
		final Partition partition = viewIdToPartition.get( new ViewId( tp.getId(), vs.getId() ) );
		final ExportMipmapInfo mipmapInfo = perSetupExportMipmapInfo.get( vs.getId() );
		final boolean writeMipmapInfo = true; // TODO: remember whether we already wrote it and write only once
		final boolean deflate = params.getDeflate();
		final ProgressWriter progressWriter = new SubTaskProgressWriter( this.progressWriter, 0.0, 1.0 ); // TODO
		final int numThreads = Math.max( 1, Runtime.getRuntime().availableProcessors() - 2 );
		WriteSequenceToHdf5.writeViewToHdf5PartitionFile( ushortimg, partition, tp.getId(), vs.getId(), mipmapInfo, writeMipmapInfo, deflate, null, null, numThreads, progressWriter );

		// update the registrations
		final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( new ViewId( tp.getId(), vs.getId() ) );

		final double scale = bb.getDownSampling();
		final AffineTransform3D m = new AffineTransform3D();
		m.set( scale, 0.0f, 0.0f, bb.min( 0 ),
			   0.0f, scale, 0.0f, bb.min( 1 ),
			   0.0f, 0.0f, scale, bb.min( 2 ) );
		final ViewTransform vt = new ViewTransformAffine( "fusion bounding box", m );

		vr.getTransformList().clear();
		vr.getTransformList().add( vt );

		return true;
	}

	@Override
	public void queryAdditionalParameters( GenericDialog gd, SpimData2 spimData )
	{
		System.out.println( "queryAdditionalParameters()" );

	}

	@Override
	public boolean parseAdditionalParameters( GenericDialog gd, SpimData2 spimData )
	{
		System.out.println( "parseAdditionalParameters()" );
		return true;
	}

	@Override
	public ImgExport newInstance()
	{
		System.out.println( "newInstance()" );
		BoundingBoxGUI.defaultPixelType = 1; // set to 16 bit by default
		return new ExportSpimData2HDF5();
	}

	@Override
	public String getDescription()
	{
		System.out.println( "getDescription()" );
		return "Save as new XML Project (HDF5)";
	}

}
