package spim.headless.definedataset;

import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.io.IOFunctions;
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
import java.util.Properties;

/**
 * DataSet definition for MicroManager
 */
public class MicroManager extends StackList
{
	public static SpimData2 createDataset( final String tiffFile, final Properties props )
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
		for ( int a = 0; a < reader.numAngles(); ++a )
			angles.add(props.getProperty( "angle_" + ( a + 1 ) ) );
		reader.setAngleNames( angles );

		final ArrayList< String > channels = new ArrayList< String >();
		for ( int c = 0; c < reader.numChannels(); ++c )
			channels.add( props.getProperty( "channel_" + ( c + 1 ) ) );
		reader.setChannelNames( channels );

		reader.setCalX( Double.parseDouble( props.getProperty( "pixel_distance_x" ) ) );
		reader.setCalY( Double.parseDouble( props.getProperty( "pixel_distance_y" ) ) );
		reader.setCalZ( Double.parseDouble( props.getProperty( "pixel_distance_z" ) ) );
		reader.setCalUnit( props.getProperty( "pixel_unit" ) );

		switch ( RotationAxis.valueOf( props.getProperty( "rotation_around", "X_Axis" ) ) )
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
}
