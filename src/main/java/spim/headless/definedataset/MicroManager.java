package spim.headless.definedataset;

import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
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
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.MicroManagerImgLoader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * DataSet definition for MicroManager
 */
public class MicroManager extends DefineDataSet
{
	public static SpimData2 createDataset( final String tiffFile, final DefineDataSetParameters params )
	{
		MultipageTiffReader reader = null;
		File mmFile = new File( tiffFile );

		try
		{
			reader = new MultipageTiffReader( mmFile );
		}
		catch ( IOException e )
		{
			IOFunctions.println( "Failed to analyze file '" + mmFile.getAbsolutePath() + "': " + e );
			return null;
		}
		final ArrayList< String > angles = new ArrayList< String >();

		if ( params.angles == null )
		{
			throw new NullPointerException( "angles[] are not provided." );
		}

		for ( int a = 0; a < reader.numAngles(); ++a )
			angles.add( params.angles[a] );
		reader.setAngleNames( angles );

		if ( params.channels == null )
		{
			throw new NullPointerException( "channels[] are not provided." );
		}
		final ArrayList< String > channels = new ArrayList< String >();
		for ( int c = 0; c < reader.numChannels(); ++c )
			channels.add( params.channels[c] );
		reader.setChannelNames( channels );

		reader.setCalX( params.pixelDistanceX );
		reader.setCalY( params.pixelDistanceY );
		reader.setCalZ( params.pixelDistanceZ );

		reader.setCalUnit( params.pixelUnit );

		switch ( params.rotationAround )
		{
			case X_Axis:
				reader.setRotAxis( new double[]{ 1, 0, 0 } );
				break;
			case Y_Axis:
				reader.setRotAxis( new double[]{ 0, 1, 0 } );
				break;
			case Z_Axis:
				reader.setRotAxis( new double[]{ 0, 0, 1 } );
				break;
		}

		reader.setApplyAxis( params.applyAxis );

		final String directory = mmFile.getParent();
		final ImgFactory< ? extends NativeType< ? > > imgFactory = new ArrayImgFactory< FloatType >();

		final SequenceDescription sequenceDescription = createSequenceDescription( reader, mmFile );
		final ImgLoader< UnsignedShortType > imgLoader = new MicroManagerImgLoader( mmFile, imgFactory, sequenceDescription );
		sequenceDescription.setImgLoader( imgLoader );

		// get the minimal resolution of all calibrations
		final double minResolution = Math.min( Math.min( reader.calX(), reader.calY() ), reader.calZ() );

		IOFunctions.println( "Minimal resolution in all dimensions is: " + minResolution );
		IOFunctions.println( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );

		// create the initial view registrations (they are all the identity transform)
		final ViewRegistrations viewRegistrations = createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );

		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimData2 spimData = new SpimData2( new File( directory ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes() );

		if ( reader.applyAxis() )
			Apply_Transformation.applyAxis( spimData );

		try { reader.close(); } catch (IOException e) { IOFunctions.println( "Could not close file '" + mmFile.getAbsolutePath() + "': " + e ); }

		return spimData;
	}

	/**
	 * Create sequence description for MultipageTiffReader.
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
