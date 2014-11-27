package spim.fiji.datasetmanager;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.IFormatReader;
import loci.formats.Modulo;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Util;
import ome.xml.model.primitives.PositiveFloat;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import fiji.util.gui.GenericDialogPlus;

public class LightSheetZ1 implements MultiViewDatasetDefinition
{
	public static String defaultFirstFile = "";
	
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
				gd.addNumericField( "Angle_" + (a+1), meta.angles()[ a ], 0, 4, "degrees" );

			gd.addMessage( "Channels (" + meta.numChannels() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
			gd.addMessage( "" );

			for ( int c = 0; c < meta.numChannels(); ++c )
				gd.addNumericField( "Channel_" + (c+1), meta.channels()[ c ], 0 );

			gd.addMessage( "Illumination Directions (" + meta.numIlluminations() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
			gd.addMessage( "" );

			for ( int i = 0; i < meta.numIlluminations(); ++i )
				gd.addNumericField( "_______Illumination_" + (i+1), 0, 0 );

			gd.addMessage( "Timepoints (" + meta.numTimepoints() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

			gd.addMessage( "Calibration", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
			gd.addMessage(
					"Pixel Distance X: " + meta.calX() + " " + meta.calUnit() + "\n" +
					"Pixel Distance Y: " + meta.calY() + " " + meta.calUnit() + "\n" +
					"Pixel Distance Z: " + meta.calZ() + " " + meta.calUnit() + "\n" );

			gd.addMessage( "Additional Meta Data", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

			String s =
					"Acquisition Objective: " + meta.objective() + "\n" +
					"Rotation axis: " + meta.rotationAxisName() + " axis\n" + 
					(meta.lightsheetThickness() < 0 ? "" : "Lighsheet thickness: " + meta.lightsheetThickness + " um\n") +
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

	public static class LightSheetZ1MetaData
	{
		private String objective = "";
		private int rotationAxis = -1;
		private int channels[];
		private int angles[];
		private int numT = -1;
		private int numI = -1;
		private double calX, calY, calZ, lightsheetThickness;
		private String[] files;
		private HashMap< Integer, int[] > imageSizes;

		public int numChannels() { return channels.length; }
		public int numAngles() { return angles.length; }
		public int numIlluminations() { return numI; }
		public int numTimepoints() { return numT; }
		public String objective() { return objective; }
		public int rotationAxis() { return rotationAxis; }
		public double calX() { return calX; }
		public double calY() { return calY; }
		public double calZ() { return calZ; }
		public String[] files() { return files; }
		public int[] channels() { return channels; }
		public int[] angles() { return angles; }
		public HashMap< Integer, int[] > imageSizes() { return imageSizes; }
		public String calUnit() { return "um"; }
		public double lightsheetThickness() { return lightsheetThickness; }
		public String rotationAxisName()
		{
			if ( rotationAxis == 0 )
				return "X";
			else if ( rotationAxis == 1 )
				return "Y";
			else if ( rotationAxis == 2 )
				return "Z";
			else
				return "Unknown";
		}

		public boolean loadMetaData( final IFormatReader r )
		{
			final Hashtable< String, Object > metaData = r.getGlobalMetadata();
			final int numA = r.getSeriesCount();

			// make sure every angle has the same amount of timepoints, channels, illuminations
			this.numT = -1;
			this.numI = -1;
			int numC = -1;
			
			// also collect the image sizes for each angle
			this.imageSizes = new HashMap< Integer, int[] >();

			for ( int a = 0; a < numA; ++a )
			{
				r.setSeries( a );

				int w = r.getSizeX();
				int h = r.getSizeY();
				int d = (int)Math.round( Double.parseDouble( metaData.get( "Information|Image|V|View|SizeZ #" + (a+1) ).toString() ) );

				imageSizes.put( a, new int[]{ w, h, d } );

				if ( numT >= 0 && numT != r.getSizeT() )
				{
					IOFunctions.println( "Number of timepoints inconsistent across angles. Stopping." );
					return false;
				}
				else
				{
					numT = r.getSizeT();
				}
				
				// Illuminations are contained within the channel count; to
				// find the number of illuminations for the current angle:
				Modulo moduloC = r.getModuloC();

				if ( numI >= 0 && numI != moduloC.length() )
				{
					IOFunctions.println( "Number of illumination directions inconsistent across angles. Stopping." );
					return false;
				}
				else
				{
					numI = moduloC.length();
				}

				if ( numC >= 0 && numC != r.getSizeC() / moduloC.length() )
				{
					IOFunctions.println( "Number of channels directions inconsistent across angles. Stopping." );
					return false;
				}
				else
				{
					numC = r.getSizeC() / moduloC.length();
				}
			}

			//
			// query details
			//
			this.channels = new int[ numC ];
			this.angles = new int[ numA ];
			this.files = r.getSeriesUsedFiles();

			Object tmp = metaData.get( "Experiment|AcquisitionBlock|AcquisitionModeSetup|Objective #1" );
			objective = (tmp != null) ? tmp.toString() : "Unknown Objective";

			for ( int c = 0; c < numC; ++c )
			{
				tmp = metaData.get( "Information|Image|Channel|Wavelength #" + ( c+1 ) );
				channels[ c ] = (tmp != null) ? (int)Math.round( Double.parseDouble( tmp.toString() ) ) : c;
			}

			boolean allAnglesNegative = true;

			for ( int a = 0; a < numA; ++a )
			{
				tmp = metaData.get( "Information|Image|V|View|Offset #" + ( a+1 ) );
				angles[ a ] = (tmp != null) ? (int)Math.round( Double.parseDouble( tmp.toString() ) ) : a;

				if ( angles[ a ] > 0 )
					allAnglesNegative = false;
			}

			if ( allAnglesNegative )
				for ( int a = 0; a < numA; ++a )
					angles[ a ] *= -1;

			tmp = metaData.get( "Information|Image|V|AxisOfRotation #1" );
			if ( tmp != null && tmp.toString().trim().length() == 5 )
			{
				final String[] axes = tmp.toString().split( " " );
				
				if ( Integer.parseInt( axes[ 0 ] ) == 1 )
					rotationAxis = 0;
				else if ( Integer.parseInt( axes[ 1 ] ) == 1 )
					rotationAxis = 1;
				else if ( Integer.parseInt( axes[ 2 ] ) == 1 )
					rotationAxis = 2;
			}

			for ( final String key : metaData.keySet() )
			{
				if ( key.startsWith( "LsmTag|Name #" ) && metaData.get( key ).toString().trim().equals( "LightSheetThickness" ) )
				{
					String lookup = "LsmTag " + key.substring( key.indexOf( '#' ), key.length() );
					tmp = metaData.get( lookup );

					if ( tmp != null )
						lightsheetThickness = Double.parseDouble( tmp.toString() );
					else
						lightsheetThickness = -1;
				}
			}

			final MetadataRetrieve retrieve = (MetadataRetrieve)r.getMetadataStore();

			float cal = 0;

			PositiveFloat f = retrieve.getPixelsPhysicalSizeX( 0 );
			if ( f != null )
				cal = f.getValue().floatValue();

			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "LightSheetZ1: Warning, calibration for dimension X seems corrupted, setting to 1." );
			}
			calX = cal;

			f = retrieve.getPixelsPhysicalSizeY( 0 );
			if ( f != null )
				cal = f.getValue().floatValue();

			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "LightSheetZ1: Warning, calibration for dimension Y seems corrupted, setting to 1." );
			}
			calY = cal;

			f = retrieve.getPixelsPhysicalSizeZ( 0 );
			if ( f != null )
				cal = f.getValue().floatValue();

			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "LightSheetZ1: Warning, calibration for dimension Z seems corrupted, setting to 1." );
			}
			calZ = cal;

			return true;
		}
	}

	public static void main( String[] args )
	{
		//defaultFirstFile = "/Volumes/My Passport/worm7/Track1(3).czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/130706_Aiptasia8.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/abe_Arabidopsis1.czi";
		defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/worm7/Track1.czi";
		new LightSheetZ1().createDataset();
	}
}
