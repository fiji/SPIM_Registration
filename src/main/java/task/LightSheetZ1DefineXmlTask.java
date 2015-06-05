package task;

import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spim.fiji.datasetmanager.LightSheetZ1MetaData;
import spim.fiji.datasetmanager.StackList;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.LightSheetZ1ImgLoader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

/**
 * Headless module for LightSheetZ1
 */
public class LightSheetZ1DefineXmlTask extends AbstractTask
{
	private static final Logger LOG = LoggerFactory.getLogger( LightSheetZ1DefineXmlTask.class );

	public static String[] rotAxes = new String[] { "X-Axis", "Y-Axis", "Z-Axis" };

	public String getTitle() { return "Zeiss Lightsheet Z.1 Dataset (LOCI Bioformats)"; }

	public static class Parameters extends AbstractTask.Parameters
	{
		private String firstFile;
		private LightSheetZ1MetaData metaData;

		public String getFirstFile()
		{
			return firstFile;
		}

		public void setFirstFile( String firstFile )
		{
			this.firstFile = firstFile;
		}

		public LightSheetZ1MetaData getMetaData()
		{
			return metaData;
		}

		public void setMetaData( LightSheetZ1MetaData metaData )
		{
			this.metaData = metaData;
		}
	}

	public static SequenceDescription createSequenceDescription( final LightSheetZ1MetaData meta, final File cziFile )
	{
		// assemble timepints, viewsetups, missingviews and the imgloader
		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >();

		for ( int t = 0; t < meta.numTimepoints(); ++t )
			timepoints.add( new TimePoint( t ) );


		final ArrayList< Channel > channels = new ArrayList< Channel >();
		for ( int c = 0; c < meta.numChannels(); ++c )
			channels.add( new Channel( c, meta.channels()[ c ] ) );

		final ArrayList< Illumination > illuminations = new ArrayList< Illumination >();
		for ( int i = 0; i < meta.numIlluminations(); ++i )
			illuminations.add( new Illumination( i, meta.illuminations()[ i ] ) );

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		for ( int a = 0; a < meta.numAngles(); ++a )
		{
			final Angle angle = new Angle( a, meta.angles()[ a ] );

			try
			{
				final double degrees = Double.parseDouble( meta.angles()[ a ] );
				double[] axis = null;

				if ( meta.rotationAxis() == 0 )
					axis = new double[]{ 1, 0, 0 };
				else if ( meta.rotationAxis() == 1 )
					axis = new double[]{ 0, 1, 0 };
				else if ( meta.rotationAxis() == 2 )
					axis = new double[]{ 0, 0, 1 };

				if ( axis != null && !Double.isNaN( degrees ) &&  !Double.isInfinite( degrees ) )
					angle.setRotation( axis, degrees );
			}
			catch ( Exception e ) {};

			angles.add( angle );
		}

		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		for ( final Channel c : channels )
			for ( final Illumination i : illuminations )
				for ( final Angle a : angles )
				{
					final VoxelDimensions voxelSize = new FinalVoxelDimensions( meta.calUnit(), meta.calX(), meta.calY(), meta.calZ() );
					final Dimensions dim = new FinalDimensions( meta.imageSizes().get( a.getId() ) );
					viewSetups.add( new ViewSetup( viewSetups.size(), null, dim, voxelSize, c, a, i ) );
				}


		// Estimate image factory
		ImgFactory< ? extends NativeType< ? > > imgFactory;
		long maxNumPixels = 0;

		for ( int a = 0; a < meta.numAngles(); ++a )
		{
			final int[] dim = meta.imageSizes().get( a );

			long n = 1;

			for ( int d = 0; d < dim.length; ++d )
				n *= (long)dim[ d ];

			maxNumPixels = Math.max( n, maxNumPixels );
		}

		int smallerLog2 = (int)Math.ceil( Math.log( maxNumPixels ) / Math.log( 2 ) );

		String s = "Maximum number of pixels in any view: n=" + maxNumPixels +
				" (2^" + (smallerLog2-1) + " < n < 2^" + smallerLog2 + " px), ";

		if ( smallerLog2 <= 31 )
		{
			LOG.info( s + "using ArrayImg." );
			imgFactory = new ArrayImgFactory< FloatType >();
		}
		else
		{
			LOG.info( s + "using CellImg(256)." );
			imgFactory = new CellImgFactory< FloatType >( 256 );
		}

		// Set imgLoader and missingViews null
		final SequenceDescription sequenceDescription = new SequenceDescription( new TimePoints( timepoints ), viewSetups, null, null );

		final ImgLoader< UnsignedShortType > imgLoader = new LightSheetZ1ImgLoader( cziFile, imgFactory, sequenceDescription );
		sequenceDescription.setImgLoader( imgLoader );

		// instantiate the sequencedescription
		return sequenceDescription;
	}

	public void process(Parameters params)
	{
		if( isDebug )
		{
			System.out.println("---- LightSheetZ1 - Parameters ----");
			super.process( params );
			System.out.println("CZI first file: " + params.getFirstFile());
			System.out.println("------------------------------------");
		}

		if(params.getMetaData() == null)
		{
			LOG.warn( "No LightSheetZ1 metadata is given. Using default setting." );

			final LightSheetZ1MetaData meta = new LightSheetZ1MetaData();

			if ( !meta.loadMetaData( new File( params.getFirstFile() ) ) )
			{
				System.out.println( "Failed to analyze file." );
				return;
			}
			params.setMetaData( meta );
		}
		else
		{
			LOG.info( "LightSheetZ1 metadata is given. Using user-defined setting." );
		}

		// Print out meta information
		final LightSheetZ1MetaData meta = params.getMetaData();

		for ( int a = 0; a < meta.numAngles(); ++a )
			LOG.info( "angle_" + (a + 1) + ":" + meta.angles()[ a ] );

		for ( int c = 0; c < meta.numChannels(); ++c )
			LOG.info( "channel_" + (c + 1) + ":" + meta.channels()[ c ] );

		for ( int i = 0; i < meta.numIlluminations(); ++i )
			LOG.info( "illumination_" + (i + 1) + ":" + meta.illuminations()[ i ] );

		LOG.info( "pixel_distance_x" + ":" + meta.calX() );
		LOG.info( "pixel_distance_y" + ":" + meta.calY() );
		LOG.info( "pixel_distance_z" + ":" + meta.calZ() );
		LOG.info( "pixel_unit" + ":" + meta.calUnit() );

		LOG.info( "rotation_around" + ":" + meta.rotationAxisName() );

		// Create the dataset
		final File cziFile = new File( params.getFirstFile() );
		final SequenceDescription sequenceDescription = createSequenceDescription( meta, cziFile );

		// get the minimal resolution of all calibrations
		final double minResolution = Math.min( Math.min( meta.calX(), meta.calY() ), meta.calZ() );

		LOG.info( "Minimal resolution in all dimensions is: " + minResolution );
		LOG.info( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );

		// create the initial view registrations (they are all the identity transform)
		final ViewRegistrations viewRegistrations = StackList.createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );

		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		spimData = new SpimData2( new File( cziFile.getParent() ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes() );

		if ( spimData == null )
		{
			LOG.info( "Defining multi-view dataset failed." );
			return;
		}
		else
		{
			SpimData2.saveXML( spimData, params.getXmlFilename(), "" );
		}
	}

	private Parameters getParams( final String[] args )
	{
		final Properties props = parseArgument( "LightSheetZ1", getTitle(), args );

		final Parameters params = new Parameters();
		params.setFirstFile( props.getProperty( "first_czi" ) );
		params.setXmlFilename( props.getProperty( "xml_filename" ) );

		// CZI metadata creation based on the options
		final LightSheetZ1MetaData meta = new LightSheetZ1MetaData();

		if ( !meta.loadMetaData( new File( params.getFirstFile() ) ))
		{
			System.err.println( "Failed to analyze file." );
			return null;
		}

		for ( int a = 0; a < meta.numAngles(); ++a )
			meta.angles()[ a ] = props.getProperty( "angle_" + (a + 1) );

		for ( int c = 0; c < meta.numChannels(); ++c )
			meta.channels()[ c ] = props.getProperty( "channel_" + ( c + 1 ) );

		for ( int i = 0; i < meta.numIlluminations(); ++i )
			meta.illuminations()[ i ] = props.getProperty( "illumination_" + ( i + 1 ) );

		meta.setCalX( Double.parseDouble( props.getProperty( "pixel_distance_x" ) ) );
		meta.setCalY( Double.parseDouble( props.getProperty( "pixel_distance_y" ) ) );
		meta.setCalZ( Double.parseDouble( props.getProperty( "pixel_distance_z" ) ) );
		meta.setCalUnit( props.getProperty( "pixel_unit" ) );

		meta.setRotationAxis( Arrays.binarySearch( rotAxes, props.getProperty( "rotation_around" ) ) );

		params.setMetaData( meta );

		return params;
	}

	@Override public void process( String[] args )
	{
		this.process( getParams( args ) );
	}

	public static void main( String[] argv )
	{
		LightSheetZ1DefineXmlTask task = new LightSheetZ1DefineXmlTask();
		task.process( argv );
		System.exit( 0 );
	}
}
