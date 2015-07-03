package spim.headless.definedataset;

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
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.LightSheetZ1ImgLoader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.io.File;
import java.util.ArrayList;
import java.util.Properties;

/**
 * DataSet definition for LightSheetZ1
 */
public class LightSheetZ1 extends DefineDataSet
{
	public static SpimData2 createDataset( final String cziFirstFile, final DefineDataSetParameters params )
	{
		final LightSheetZ1MetaData meta = new LightSheetZ1MetaData();

		if ( !meta.loadMetaData( new File( cziFirstFile ) ) )
		{
			IOFunctions.println( "Failed to analyze file." );
			return null;
		}

		if(params.angles != null)
				for ( int a = 0; a < meta.numAngles(); ++a )
					meta.angles()[ a ] = params.angles[a];

		if(params.channels != null)
			for ( int c = 0; c < meta.numChannels(); ++c )
				meta.channels()[ c ] = params.channels[c];

		if(params.illuminations != null)
			for ( int i = 0; i < meta.numIlluminations(); ++i )
				meta.illuminations()[ i ] = params.illuminations[i];

		if(params.pixelDistanceX != 0)
			meta.setCalX( params.pixelDistanceX );

		if(params.pixelDistanceY != 0)
			meta.setCalY( params.pixelDistanceY );

		if(params.pixelDistanceZ != 0)
			meta.setCalZ( params.pixelDistanceZ );

		meta.setCalUnit( params.pixelUnit );

		switch ( params.rotationAround )
		{
			case X_Axis:
				meta.setRotationAxis( 0 );
				break;
			case Y_Axis:
				meta.setRotationAxis( 1 );
				break;
			case Z_Axis:
				meta.setRotationAxis( 2 );
				break;
		}

		final File cziFile = new File( cziFirstFile );
		final SequenceDescription sequenceDescription = createSequenceDescription( meta, cziFile );

		// get the minimal resolution of all calibrations
		final double minResolution = Math.min( Math.min( meta.calX(), meta.calY() ), meta.calZ() );

		IOFunctions.println( "Minimal resolution in all dimensions is: " + minResolution );
		IOFunctions.println( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );

		// create the initial view registrations (they are all the identity transform)
		final ViewRegistrations viewRegistrations = createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );

		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		return new SpimData2( new File( cziFile.getParent() ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes() );
	}

	/**
	 * Create sequence description for LightSheetZ1MetaData.
	 *
	 * @param meta the meta
	 * @param cziFile the czi file
	 * @return the sequence description
	 */
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
			IOFunctions.println( s + "using ArrayImg." );
			imgFactory = new ArrayImgFactory< FloatType >();
		}
		else
		{
			IOFunctions.println( s + "using CellImg(256)." );
			imgFactory = new CellImgFactory< FloatType >( 256 );
		}

		// Set imgLoader and missingViews null
		final SequenceDescription sequenceDescription = new SequenceDescription( new TimePoints( timepoints ), viewSetups, null, null );

		final ImgLoader< UnsignedShortType > imgLoader = new LightSheetZ1ImgLoader( cziFile, imgFactory, sequenceDescription );
		sequenceDescription.setImgLoader( imgLoader );

		// instantiate the sequencedescription
		return sequenceDescription;
	}

}
