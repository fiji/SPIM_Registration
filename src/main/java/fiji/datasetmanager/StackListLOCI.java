package fiji.datasetmanager;

import ij.IJ;

import java.io.File;
import java.io.IOException;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.data.SpimData;

public class StackListLOCI extends StackList
{
	@Override
	public String getTitle() 
	{
		return "3d Image Stacks (LOCI Bioformats)";
	}

	@Override
	public SpimData<?, ?> createDataset()
	{
		System.out.println( queryInformation() );
		
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getExtendedDescription()
	{
		return  "This dataset definition supports a series of three-dimensional (3d) image stacks\n" +  
				 "all present in the same folder. The filename of each file must define timepoint,\n" +  
				 "angle, channel and illumination direction (or a subset of those).\n" + 
				 "The 3d image stacks can be stored in any fileformat that LOCI Bioformats is able\n" + 
				 "to import, for example TIFF, LSM, CZI, ...\n" + 
				 "\n" + 
				 "The filenames of the 3d image stacks could be for example:\n" +
				 "\n" + 
				 "spim_TL1_Channel1_Illum1_Angle0.tif ... spim_TL100_Channel2_Illum2_Angle315.tif\n" + 
				 "data_TP01_Angle000.lsm ... data_TP70_Angle180.lsm\n" +
				 "Angle0.ome.tiff ... Angle288.ome.tiff\n" +
				 "\n" +
				 "Note: this definition can be used for OpenSPIM data.";
	}

	@Override
	protected boolean loadCalibration( final File file )
	{
		System.out.println( "Loading calibration for: " + file.getAbsolutePath() );
		
		final IFormatReader r = new ChannelSeparator();

		if ( !createOMEXMLMetadata( r ) ) 
		{
			try 
			{
				r.close();
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
			return false;
		}
		
		
		try 
		{
			r.setId( file.getAbsolutePath() );
			
			final MetadataRetrieve retrieve = (MetadataRetrieve)r.getMetadataStore();
			
			float cal = retrieve.getPixelsPhysicalSizeX( 0 ).getValue().floatValue();
			if ( cal == 0 )
			{
				cal = 1;
				System.out.println( "StackListLOCI: Warning, calibration for dimension X seems corrupted, setting to 1." );
			}
			calX = cal;

			cal = retrieve.getPixelsPhysicalSizeY( 0 ).getValue().floatValue();
			if ( cal == 0 )
			{
				cal = 1;
				System.out.println( "StackListLOCI: Warning, calibration for dimension Y seems corrupted, setting to 1." );
			}
			calY = cal;

			cal = retrieve.getPixelsPhysicalSizeZ( 0 ).getValue().floatValue();
			if ( cal == 0 )
			{
				cal = 1;
				System.out.println( "StackListLOCI: Warning, calibration for dimension Z seems corrupted, setting to 1." );
			}
			calZ = cal;
			
			r.close();
		} 
		catch ( Exception e) 
		{
			IJ.log( "Could not open file: '" + file.getAbsolutePath() + "'" );
			e.printStackTrace();
			return false;
		}
		
		return true;
	}

	private static boolean createOMEXMLMetadata(final IFormatReader r)
	{
		try {
			final ServiceFactory serviceFactory = new ServiceFactory();
			final OMEXMLService service = serviceFactory
					.getInstance(OMEXMLService.class);
			final IMetadata omexmlMeta = service.createOMEXMLMetadata();
			r.setMetadataStore(omexmlMeta);
		} catch (final ServiceException e) {
			e.printStackTrace();
			return false;
		} catch (final DependencyException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

}
