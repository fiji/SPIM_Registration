package spim.process.fusion.export;

import ij.gui.GenericDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import spim.fiji.plugin.resave.Generic_Resave_HDF5;
import spim.fiji.plugin.resave.Generic_Resave_HDF5.Parameters;
import spim.fiji.plugin.resave.ProgressWriterIJ;
import spim.fiji.plugin.resave.Resave_HDF5;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import bdv.spimdata.tools.MergePartitionList;

public class AppendSpimData2HDF5 implements ImgExport
{
	public static String defaultPath = null;

	private List< TimePoint > newTimepoints;

	private List< ViewSetup > newViewSetups;

	private Parameters params;

	private SpimData2 spimData;

	private SpimData2 fusionOnlySpimData;

	private Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo;

	private HashMap< ViewId, Partition > viewIdToPartition;

	private final ProgressWriter progressWriter = new ProgressWriterIJ();

	@Override
	public boolean finish()
	{
		// this spimdata object was modified
		return true;
	}

	@Override
	public void setXMLData ( final List< TimePoint > newTimepoints, final List< ViewSetup > newViewSetups )
	{
		this.newTimepoints = newTimepoints;
		this.newViewSetups = newViewSetups;
	}

	@Override
	public boolean queryParameters( final SpimData2 spimData, final boolean is16bit )
	{
		System.out.println( "queryParameters()" );

		if ( newTimepoints == null || newViewSetups == null )
		{
			IOFunctions.println( "new timepoints and new viewsetup list not set yet ... cannot continue" );
			return false;
		}

		Hdf5ImageLoader il = ( Hdf5ImageLoader ) spimData.getSequenceDescription().getImgLoader();

		perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( newViewSetups );

		String fn = il.getHdf5File().getAbsolutePath();
		if ( fn.endsWith( ".h5" ) )
			fn = fn.substring( 0, fn.length() - ".h5".length() );
		String fusionHdfFilename = "";
		String fusionXmlFilename = "";
		for ( int i = 0;; ++i )
		{
			fusionHdfFilename = String.format( "%s-f%d.h5", fn, i );
			fusionXmlFilename = String.format( "%s-f%d.xml", fn, i );
			if ( !new File( fusionHdfFilename ).exists() && !new File( fusionXmlFilename ).exists() )
				break;
		}

		final int firstviewSetupId = newViewSetups.get( 0 ).getId();
		params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), false, getDescription(), is16bit );
		if ( params == null )
		{
			System.out.println( "abort " );
			return false;
		}
		params.setHDF5File( new File( fusionHdfFilename ) );
		params.setSeqFile( new File( fusionXmlFilename ) );

		Pair< SpimData2, HashMap< ViewId, Partition > > init = ExportSpimData2HDF5.initSpimData(
				newTimepoints, newViewSetups, params, perSetupExportMipmapInfo );
		fusionOnlySpimData = init.getA();
		viewIdToPartition = init.getB();

		perSetupExportMipmapInfo.putAll(
				MergePartitionList.getHdf5PerSetupExportMipmapInfos( spimData.getSequenceDescription() ) );

		this.spimData = spimData;
		AppendSpimData2.appendSpimData2( spimData, newTimepoints, newViewSetups );

		ArrayList< Partition > mergedPartitions = MergePartitionList.getMergedHdf5PartitionList(
				spimData.getSequenceDescription(), fusionOnlySpimData.getSequenceDescription() );

		String mergedHdfFilename = "";
		for ( int i = 0;; ++i )
		{
			mergedHdfFilename = String.format( "%s-m%d.h5", fn, i );
			if ( !new File( mergedHdfFilename ).exists() )
				break;
		}

		SequenceDescription seq = spimData.getSequenceDescription();
		Hdf5ImageLoader newLoader = new Hdf5ImageLoader(
				new File( mergedHdfFilename ), mergedPartitions, seq, false );
		seq.setImgLoader( newLoader );
		WriteSequenceToHdf5.writeHdf5PartitionLinkFile( spimData.getSequenceDescription(), perSetupExportMipmapInfo );

		return true;
	}

	@Override
	public < T extends RealType< T > & NativeType< T >> boolean exportImage( RandomAccessibleInterval< T > img, BoundingBoxGUI bb, TimePoint tp, ViewSetup vs )
	{
		System.out.println( "exportImage1()" );
		return exportImage( img, bb, tp, vs, Double.NaN, Double.NaN );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( RandomAccessibleInterval< T > img, BoundingBoxGUI bb, TimePoint tp, ViewSetup vs, double min, double max )
	{
		System.out.println( "exportImage2()" );

		// write the image
		final RandomAccessibleInterval< UnsignedShortType > ushortimg;
		if ( ! UnsignedShortType.class.isInstance( Util.getTypeFromInterval( img ) ) )
			ushortimg = ExportSpimData2HDF5.convert( img, params );
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
	public void queryAdditionalParameters( final GenericDialog gd, final SpimData2 spimData )
	{}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd, final SpimData2 spimData )
	{
		return true;
	}

	@Override
	public ImgExport newInstance()
	{
		BoundingBoxGUI.defaultPixelType = 1; // set to 16 bit by default
		return new AppendSpimData2HDF5();
	}

	@Override
	public String getDescription()
	{
		return "Append to current XML Project (HDF5)";
	}
}
