package spim.headless.definedataset;

import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.io.File;
import java.util.Properties;

/**
 * DataSet definition for LightSheetZ1
 */
public class LightSheetZ1 extends StackList
{
	public static SpimData2 createDataset( final String cziFirstFile, final Properties props )
	{
		final LightSheetZ1MetaData meta = new LightSheetZ1MetaData();

		if ( !meta.loadMetaData( new File( cziFirstFile ) ) )
		{
			IOFunctions.println( "Failed to analyze file." );
			return null;
		}

		for ( int a = 0; a < meta.numAngles(); ++a )
			meta.angles()[ a ] = props.getProperty( "angle_" + ( a + 1 ) );

		for ( int c = 0; c < meta.numChannels(); ++c )
			meta.channels()[ c ] = props.getProperty( "channel_" + ( c + 1 ) );

		for ( int i = 0; i < meta.numIlluminations(); ++i )
			meta.illuminations()[ i ] = props.getProperty( "illumination_" + ( i + 1 ) );

		meta.setCalX( Double.parseDouble( props.getProperty( "pixel_distance_x" ) ) );
		meta.setCalY( Double.parseDouble( props.getProperty( "pixel_distance_y" ) ) );
		meta.setCalZ( Double.parseDouble( props.getProperty( "pixel_distance_z" ) ) );
		meta.setCalUnit( props.getProperty( "pixel_unit" ) );

		switch ( RotationAxis.valueOf( props.getProperty( "rotation_around", "X_Axis" ) ) )
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
}
