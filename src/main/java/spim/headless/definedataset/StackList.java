package spim.headless.definedataset;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.spimdata.imgloaders.LightSheetZ1ImgLoader;
import spim.fiji.spimdata.imgloaders.MicroManagerImgLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Base StackList class for various kinds of DataSet definition class
 */
public class StackList
{
	public static enum RotationAxis
	{
		X_Axis, Y_Axis, Z_Axis
	};
	/**
	 * Assembles the {@link mpicbg.spim.data.registration.ViewRegistration} object consisting of a list of {@link mpicbg.spim.data.registration.ViewRegistration}s for all {@link mpicbg.spim.data.sequence.ViewDescription}s that are present
	 *
	 * @param viewDescriptionList
	 * @param minResolution - the smallest resolution in any dimension (distance between two pixels in the output image will be that wide)
	 * @return
	 */
	protected static ViewRegistrations createViewRegistrations( final Map< ViewId, ViewDescription > viewDescriptionList, final double minResolution )
	{
		final HashMap< ViewId, ViewRegistration > viewRegistrationList = new HashMap< ViewId, ViewRegistration >();

		for ( final ViewDescription viewDescription : viewDescriptionList.values() )
			if ( viewDescription.isPresent() )
			{
				final ViewRegistration viewRegistration = new ViewRegistration( viewDescription.getTimePointId(), viewDescription.getViewSetupId() );

				final VoxelDimensions voxelSize = viewDescription.getViewSetup().getVoxelSize();

				final double calX = voxelSize.dimension( 0 ) / minResolution;
				final double calY = voxelSize.dimension( 1 ) / minResolution;
				final double calZ = voxelSize.dimension( 2 ) / minResolution;

				final AffineTransform3D m = new AffineTransform3D();
				m.set( calX, 0.0f, 0.0f, 0.0f,
						0.0f, calY, 0.0f, 0.0f,
						0.0f, 0.0f, calZ, 0.0f );
				final ViewTransform vt = new ViewTransformAffine( "calibration", m );
				viewRegistration.preconcatenateTransform( vt );

				viewRegistrationList.put( viewRegistration, viewRegistration );
			}

		return new ViewRegistrations( viewRegistrationList );
	}

	/**
	 * Create sequence description.
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


	/**
	 * Create sequence description.
	 *
	 * @param meta the meta
	 * @param mmFile the micromanager file
	 * @return the sequence description
	 */
	public static SequenceDescription createSequenceDescription( final MultipageTiffReader meta, final File mmFile  )
	{
		// assemble timepints, viewsetups, missingviews and the imgloader
		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >();

		for ( int t = 0; t < meta.numTimepoints(); ++t )
			timepoints.add( new TimePoint( t ) );


		final ArrayList< Channel > channels = new ArrayList< Channel >();
		for ( int c = 0; c < meta.numChannels(); ++c )
			channels.add( new Channel( c, meta.channelName( c ) ) );

		final ArrayList< Illumination > illuminations = new ArrayList< Illumination >();
		for ( int i = 0; i < meta.numPositions(); ++i )
			illuminations.add( new Illumination( i, String.valueOf( i ) ) );

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		for ( int a = 0; a < meta.numAngles(); ++a )
		{
			final Angle angle = new Angle( a, meta.rotationAngle( a ) );

			try
			{
				final double degrees = Double.parseDouble( meta.rotationAngle( a ) );
				double[] axis = meta.rotationAxis();

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
					final Dimensions dim = new FinalDimensions( new long[]{ meta.width(), meta.height(), meta.depth() } );
					viewSetups.add( new ViewSetup( viewSetups.size(), null, dim, voxelSize, c, a, i ) );
				}

		final MissingViews missingViews = null;

		// Estimate image factory
		final ImgFactory< ? extends NativeType< ? > > imgFactory = new ArrayImgFactory< FloatType >();

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( new TimePoints( timepoints ), viewSetups, null, missingViews );
		final ImgLoader< UnsignedShortType > imgLoader = new MicroManagerImgLoader( mmFile, imgFactory, sequenceDescription );
		sequenceDescription.setImgLoader( imgLoader );

		// instantiate the sequencedescription
		return sequenceDescription;
	}

}
