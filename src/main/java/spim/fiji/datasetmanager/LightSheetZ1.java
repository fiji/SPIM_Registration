package spim.fiji.datasetmanager;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Util;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import fiji.util.gui.GenericDialogPlus;

public class LightSheetZ1 implements MultiViewDatasetDefinition
{
	public static String defaultFirstFile = "";
	public static boolean defaultModifyCal = false;

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

		try
		{
			r.setId( firstFile.getAbsolutePath() );

			final LightSheetZ1MetaData meta = new LightSheetZ1MetaData();
			
			if ( !meta.loadMetaData( r ) )
			{
				IOFunctions.println( "Failed to analyze file." );
				return null;
			}

			printMetaData( r );

			r.close();

			gd = new GenericDialogPlus( "Lightsheet Z.1 Properties" );

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
			gd.addMessage(
					"Pixel Distance X: " + meta.calX() + " " + meta.calUnit() + "\n" +
					"Pixel Distance Y: " + meta.calY() + " " + meta.calUnit() + "\n" +
					"Pixel Distance Z: " + meta.calZ() + " " + meta.calUnit() + "\n" );
			gd.addCheckbox( "Modify_calibration", defaultModifyCal );

			gd.addMessage( "Additional Meta Data", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

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
				return null;

			System.out.println( "num angles: " + meta.numAngles() );
			System.out.println( "num channels: " + meta.numChannels() );
			System.out.println( "num illums: " + meta.numIlluminations() );
			System.out.println( "num timepoints: " + meta.numTimepoints() );
			
			System.out.println( "Obj: " + meta.objective() );
			for ( int c = 0; c < meta.numChannels(); ++c )
				System.out.println( "Channel " + c + ": " + meta.channels()[ c ] );
			for ( int a = 0; a < meta.numAngles(); ++a )
				System.out.println( "Angle " + a + ": " + meta.angles()[ a ] + ", dim=" + Util.printCoordinates( meta.imageSizes().get( a ) ) );
			System.out.println( "Rotation axis dimension: " + meta.rotationAxis() );
			System.out.println( "calX: " + meta.calX() );
			System.out.println( "calY: " + meta.calY() );
			System.out.println( "calZ: " + meta.calZ() );

			
		}
		catch ( Exception e )
		{
			IOFunctions.println( "File '" + firstFile.getAbsolutePath() + "' could not be opened: " + e );
			IOFunctions.println( "Stopping" );

			e.printStackTrace();
			return null;
		}

		// TODO Auto-generated method stub
		return null;
	}

	public static void printMetaData( final IFormatReader r )
	{
		final Hashtable< String, Object > metaData = r.getGlobalMetadata();

		ArrayList< String > entries = new ArrayList<String>();

		for ( final String s : metaData.keySet() )
			entries.add( "'" + s + "': " + metaData.get( s ) );

		Collections.sort( entries );

		for ( final String s : entries )
			System.out.println( s );
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
