package spim.fiji.datasetmanager;

import java.io.File;
import java.io.IOException;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.Modulo;
import loci.formats.in.ZeissCZIReader;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import fiji.util.gui.GenericDialogPlus;
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
		
		// should I use the ZeissCZIReader here directly?
		final ZeissCZIReader r = new ZeissCZIReader();
		
		// is that still necessary?
		if ( !createOMEXMLMetadata( r ) )
		{
			try { r.close(); } catch (IOException e) { e.printStackTrace(); }
			return null;
		}
		
		try
		{
			r.setId( firstFile.getAbsolutePath() );
			
			String[] files = r.getSeriesUsedFiles();
			
			for ( final String f : files )
				System.out.println( f );

			System.out.println( r.getSeriesCount() );
									
			
			for (int angle=0; angle<r.getSeriesCount(); angle++)
			{
				r.setSeries( angle );
				
				System.out.println( "Angle: " + angle );
				
				System.out.println( "x: " + r.getSizeX() );
				System.out.println( "y: " + r.getSizeY() );
				System.out.println( "z: " + r.getSizeZ() );
				System.out.println( "t: " + r.getSizeT() );
				System.out.println( "c: " + r.getSizeC() );
				
				Modulo moduloC = r.getModuloC();
				int illuminations = moduloC.length();
				
				System.out.println( "i: " + illuminations );
			}
			
			// now I should be able to query all the details, right?

			// and later on open the requested data

		} catch ( Exception e ) { e.printStackTrace(); }
		
		// TODO Auto-generated method stub
		return null;
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
		defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/130706_Aiptasia8.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/abe_Arabidopsis1.czi";
		new LightSheetZ1().createDataset();
	}
}
