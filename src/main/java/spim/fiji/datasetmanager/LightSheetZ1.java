package spim.fiji.datasetmanager;

import fiji.util.gui.GenericDialogPlus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;

import ome.xml.meta.MetadataStore;
import ome.xml.model.primitives.PositiveFloat;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.CoreMetadata;
import loci.formats.IFormatReader;
import loci.formats.Modulo;
import loci.formats.in.ZeissCZIReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.SpimData2;

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
		final GenericDialogPlus gd = new GenericDialogPlus( "Define Lightsheet Z.1 Dataset" );

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

			// files used in this acquisition
			final String[] files = r.getSeriesUsedFiles();

			final int numA = r.getSeriesCount();

			// make sure every angle has the same amount of timepoints, channels, illuminations
			int numT = -1;
			int numC = -1;
			int numI = -1;

			for ( int angle = 0; angle < numA; ++angle )
			{
				r.setSeries( angle );

				System.out.println( "x: " + r.getSizeX() );
				System.out.println( "y: " + r.getSizeY() );
				System.out.println( "z: " + r.getSizeZ() );

				if ( numT >= 0 && numT != r.getSizeT() )
				{
					IOFunctions.println( "Number of timepoints inconsistent across angles. Stopping." );
					r.close();
					return null;
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
					r.close();
					return null;
				}
				else
				{
					numI = moduloC.length();
				}

				if ( numC >= 0 && numC != r.getSizeC() / moduloC.length() )
				{
					IOFunctions.println( "Number of channels directions inconsistent across angles. Stopping." );
					r.close();
					return null;
				}
				else
				{
					numC = r.getSizeC() / moduloC.length();
				}
			}

			System.out.println( "num angles: " + numA );
			System.out.println( "num channels: " + numC );
			System.out.println( "num illums: " + numI );
			System.out.println( "num timepoints: " + numT );

			//
			// query details
			//
			String objective = "";
			int rotationAxis = -1;
			int channels[] = new int[ numC ];
			int angles[] = new int[ numA ];
			double calX, calY, calZ;

			final Hashtable< String, Object > metaData = r.getGlobalMetadata();
			
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

			System.out.println( "Obj: " + objective );
			for ( int c = 0; c < numC; ++c )
				System.out.println( "Channel " + c + ": " + channels[ c ] );
			for ( int a = 0; a < numA; ++a )
				System.out.println( "Angle " + a + ": " + angles[ a ] );
			System.out.println( "Rotation axis: " + rotationAxis );
			System.out.println( "calX: " + calX );
			System.out.println( "calY: " + calY );
			System.out.println( "calZ: " + calZ );
			//printMetaData( metaData );

			r.close();
			
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

	private static void printMetaData( final Hashtable< String, Object > metaData )
	{
		ArrayList< String > entries = new ArrayList<String>();

		for ( final String s : metaData.keySet() )
			if ( s.startsWith( "Experiment|AcquisitionBlock" ) )
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
