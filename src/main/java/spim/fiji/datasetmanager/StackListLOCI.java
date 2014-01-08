package spim.fiji.datasetmanager;

import java.io.File;
import java.io.IOException;

import spim.fiji.spimdata.imgloaders.StackImgLoaderLOCI;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;

public class StackListLOCI extends StackList
{
	public static int defaultAngleChoice = 1;
	public static int defaultTimePointChoice = 1;
	public static int defaultChannelleChoice = 0;
	public static int defaultIlluminationChoice = 0;

	@Override
	public String getTitle() 
	{
		return "Image Stacks (LOCI Bioformats)";
	}

	@Override
	protected ImgLoader createAndInitImgLoader( final String path, final File basePath, final ImgFactory< ? extends NativeType< ? > > imgFactory )
	{
		final StackImgLoaderLOCI imgLoader = new StackImgLoaderLOCI();
		
		imgLoader.init( path, basePath, fileNamePattern, imgFactory, hasMultipleTimePoints, hasMultipleChannels, hasMultipleIlluminations, hasMultipleAngles );
		
		return imgLoader;
	}

	@Override
	public String getExtendedDescription()
	{
		return  "This dataset definition supports series of image stacks all present in the same\n" +  
				 "folder. The filename of each file is defined by timepoint, angle, channel and\n" +  
				 "illumination direction or multiple timepoints and channels per file.\n" + 
				 "The image stacks can be stored in any fileformat that LOCI Bioformats is able\n" + 
				 "to import, for example TIFF, LSM, CZI, ...\n" + 
				 "\n" + 
				 "The filenames of the 3d image stacks could be for example:\n" +
				 "\n" + 
				 "spim_TL1_Ill1_Angle0.tif ... spim_TL100_Ill2_Angle315.tif [2 channels each]\n" + 
				 "data_TP01_Angle000.lsm ... data_TP70_Angle180.lsm\n" +
				 "Angle0.ome.tiff ... Angle288.ome.tiff\n" +
				 "\n" +
				 "Note: this definition can be used for OpenSPIM data.";
	}

	@Override
	protected boolean loadCalibration( final File file )
	{
		IOFunctions.println( "Loading calibration for: " + file.getAbsolutePath() );
				
		if ( !file.exists() )
		{
			IOFunctions.println( "File '" + file + "' does not exist. Stopping." );
			return false;
		}

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
				IOFunctions.println( "StackListLOCI: Warning, calibration for dimension X seems corrupted, setting to 1." );
			}
			calX = cal;

			cal = retrieve.getPixelsPhysicalSizeY( 0 ).getValue().floatValue();
			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "StackListLOCI: Warning, calibration for dimension Y seems corrupted, setting to 1." );
			}
			calY = cal;

			cal = retrieve.getPixelsPhysicalSizeZ( 0 ).getValue().floatValue();
			if ( cal == 0 )
			{
				cal = 1;
				IOFunctions.println( "StackListLOCI: Warning, calibration for dimension Z seems corrupted, setting to 1." );
			}
			calZ = cal;
			
			r.close();
		} 
		catch ( Exception e) 
		{
			IOFunctions.println( "Could not open file: '" + file.getAbsolutePath() + "'" );
			e.printStackTrace();
			return false;
		}
		
		return true;
	}

	public static boolean createOMEXMLMetadata(final IFormatReader r)
	{
		try 
		{
			final ServiceFactory serviceFactory = new ServiceFactory();
			final OMEXMLService service = serviceFactory.getInstance(OMEXMLService.class);
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
	protected boolean supportsMultipleTimepointsPerFile() { return true; }

	@Override
	protected boolean supportsMultipleChannelsPerFile() { return true; }

	@Override
	protected boolean supportsMultipleAnglesPerFile() { return false; }

	@Override
	protected boolean supportsMultipleIlluminationsPerFile() { return false; }
	
	@Override
	protected int getDefaultMultipleAngles() { return defaultAngleChoice; }

	@Override
	protected int getDefaultMultipleTimepoints() { return defaultTimePointChoice; }

	@Override
	protected int getDefaultMultipleChannels() { return defaultChannelleChoice; }

	@Override
	protected int getDefaultMultipleIlluminations() { return defaultIlluminationChoice; }

	@Override
	protected void setDefaultMultipleAngles( final int a ) { defaultAngleChoice = a; }

	@Override
	protected void setDefaultMultipleTimepoints( final int t ) { defaultTimePointChoice = t; }

	@Override
	protected void setDefaultMultipleChannels( final int c ) { defaultChannelleChoice = c; }

	@Override
	protected void setDefaultMultipleIlluminations( final int i ) { defaultIlluminationChoice = i; }

	@Override
	public StackListLOCI newInstance() { return new StackListLOCI(); }
}
