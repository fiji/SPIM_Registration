package spim.fiji.datasetmanager;

import ij.gui.GenericDialog;

import java.awt.Font;
import java.io.File;
import java.io.IOException;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import fiji.util.gui.GenericDialogPlus;

public class LightSheetZ1 implements MultiViewDatasetDefinition
{
	public static String[] rotAxes = new String[] { "X-Axis", "Y-Axis", "Z-Axis" };

	public static String defaultFirstFile = "";
	public static boolean defaultModifyCal = false;
	public static boolean defaultRotAxis = false;

	@Override
	public String getTitle() { return "Zeiss Lightsheet Z.1 Dataset"; }

	@Override
	public String getExtendedDescription()
	{
		return "This datset definition supports files saved by the Zeiss Lightsheet Z.1\n" +
				"microscope. By default, one file per time-point is saved by Zen, which includes\n" +
				"all angles, channels and illumination directions. We support this format and\n" +
				"most other combinations that can be saved.\n" +
				"\n" +
				"Note: if you want to process multiple CZI datasets that are actually one experi-\n" +
				"ment (e.g. two channels individually acquired), please re-save them in Zen as\n" +
				"CZI files containing only one 3d stack per file and use the dataset definition\n" +
				"'3d Image Stacks (LOCI Bioformats)'";
	}

	@Override
	public SpimData2 createDataset()
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Define Lightsheet Z.1 Dataset" );

		gd.addFileField( "First_CZI file of the dataset", defaultFirstFile, 50 );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		final File firstFile = new File( defaultFirstFile = gd.getNextString() );

		if ( !firstFile.exists() )
		{
			IOFunctions.println( "File '" + firstFile.getAbsolutePath() + "' does not exist. Stopping" );
			return null;
		}
		else
		{
			IOFunctions.println( "Investigating file '" + firstFile.getAbsolutePath() + "'." );
		}

		// should I use the ZeissCZIReader here directly?
		final IFormatReader r = new ChannelSeparator();// new ZeissCZIReader();

		// is that still necessary?
		if ( !createOMEXMLMetadata( r ) )
		{
			try { r.close(); } catch (IOException e) { e.printStackTrace(); }
			IOFunctions.println( "Creating MetaDataStore failed. Stopping" );
			return null;
		}

		LightSheetZ1MetaData meta = new LightSheetZ1MetaData();

		try
		{
			r.setId( firstFile.getAbsolutePath() );

			if ( !meta.loadMetaData( r ) )
			{
				IOFunctions.println( "Failed to analyze file." );
				return null;
			}

			//LightSheetZ1MetaData.printMetaData( r );

			r.close();
			
		}
		catch ( Exception e )
		{
			IOFunctions.println( "File '" + firstFile.getAbsolutePath() + "' could not be opened: " + e );
			IOFunctions.println( "Stopping" );

			e.printStackTrace();
			meta = null;
		}

		if ( meta == null || !showDialogs( meta ) )
			return null;

		
		// TODO Auto-generated method stub
		return null;
	}

	protected boolean showDialogs( final LightSheetZ1MetaData meta )
	{
		GenericDialog gd = new GenericDialog( "Lightsheet Z.1 Properties" );

		gd.addMessage( "Angles (" + meta.numAngles() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int a = 0; a < meta.numAngles(); ++a )
			gd.addNumericField( "Angle_" + (a+1) + ":", meta.angles()[ a ], 0 );

		gd.addMessage( "Channels (" + meta.numChannels() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int c = 0; c < meta.numChannels(); ++c )
			gd.addNumericField( "Channel_" + (c+1) + ":", meta.channels()[ c ], 0 );

		gd.addMessage( "Illumination Directions (" + meta.numIlluminations() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int i = 0; i < meta.numIlluminations(); ++i )
			gd.addNumericField( "_______Illumination_" + (i+1) + ":", 0, 0 );

		gd.addMessage( "Timepoints (" + meta.numTimepoints() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		gd.addMessage( "Calibration", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addCheckbox( "Modify_calibration", defaultModifyCal );
		gd.addMessage(
				"Pixel Distance X: " + meta.calX() + " " + meta.calUnit() + "\n" +
				"Pixel Distance Y: " + meta.calY() + " " + meta.calUnit() + "\n" +
				"Pixel Distance Z: " + meta.calZ() + " " + meta.calUnit() + "\n" );

		gd.addMessage( "Additional Meta Data", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );
		gd.addCheckbox( "Modify_rotation_axis", defaultRotAxis );

		String s =
				"Acquisition Objective: " + meta.objective() + "\n" +
				"Rotation axis: " + meta.rotationAxisName() + " axis\n" + 
				(meta.lightsheetThickness() < 0 ? "" : "Lighsheet thickness: " + meta.lightsheetThickness() + " um\n") +
				"Dataset directory: " + new File( meta.files()[ 0 ] ).getParent() + "\n" +
				"Dataset files: \n";

		for ( int i = 0; i < meta.files().length; ++i )
			s += "     " + new File( meta.files()[ i ] ).getName() + "\n";

		gd.addMessage( s, new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );

		GUIHelper.addScrollBars( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;

		final boolean modifyCal = defaultModifyCal = gd.getNextBoolean();
		final boolean modifyAxis = defaultRotAxis = gd.getNextBoolean();

		if ( modifyAxis || modifyCal )
		{
			gd = new GenericDialog( "Modify Meta Data" );

			if ( modifyCal )
			{
				gd.addNumericField( "Pixel_distance_x", meta.calX(), 5 );
				gd.addNumericField( "Pixel_distance_y", meta.calY(), 5 );
				gd.addNumericField( "Pixel_distance_z", meta.calZ(), 5 );
				gd.addStringField( "Pixel_unit", meta.calUnit() );
			}

			if ( modifyAxis )
			{
				if ( meta.rotationAxis() < 0 )
					meta.setRotationAxis( 0 );

				gd.addChoice( "Rotation_around", rotAxes, rotAxes[ meta.rotationAxis() ] );
			}

			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			if ( modifyCal )
			{
				meta.setCalX( gd.getNextNumber() );
				meta.setCalY( gd.getNextNumber() );
				meta.setCalZ( gd.getNextNumber() );
				meta.setCalUnit( gd.getNextString() );
			}

			if ( modifyAxis )
				meta.setRotationAxis( gd.getNextChoiceIndex() );
		}

		return true;
	}

	public static boolean createOMEXMLMetadata( final IFormatReader r )
	{
		try 
		{
			final ServiceFactory serviceFactory = new ServiceFactory();
			final OMEXMLService service = serviceFactory.getInstance( OMEXMLService.class );
			final IMetadata omexmlMeta = service.createOMEXMLMetadata();
			r.setMetadataStore(omexmlMeta);
		}
		catch (final ServiceException e)
		{
			e.printStackTrace();
			return false;
		}
		catch (final DependencyException e)
		{
			e.printStackTrace();
			return false;
		}
		
		return true;
	}

	@Override
	public LightSheetZ1 newInstance() { return new LightSheetZ1(); }

	public static void main( String[] args )
	{
		//defaultFirstFile = "/Volumes/My Passport/worm7/Track1(3).czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/130706_Aiptasia8.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/abe_Arabidopsis1.czi";
		defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/worm7/Track1.czi";
		new LightSheetZ1().createDataset();
	}
}
