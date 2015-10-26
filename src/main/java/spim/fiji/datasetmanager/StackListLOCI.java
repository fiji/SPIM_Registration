package spim.fiji.datasetmanager;

import java.io.File;

import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import spim.fiji.spimdata.imgloaders.LegacyStackImgLoaderLOCI;
import spim.fiji.spimdata.imgloaders.StackImgLoader;
import spim.fiji.spimdata.imgloaders.StackImgLoaderLOCI;

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
	protected StackImgLoader< ? > createAndInitImgLoader( final String path, final File basePath, final ImgFactory< ? extends NativeType< ? > > imgFactory, SequenceDescription sequenceDescription )
	{
		return new StackImgLoaderLOCI(
				new File( basePath.getAbsolutePath(), path ),
				fileNamePattern, imgFactory,
				hasMultipleTimePoints, hasMultipleChannels, hasMultipleIlluminations, hasMultipleAngles,
				sequenceDescription );
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
	protected Calibration loadCalibration( final File file )
	{
		IOFunctions.println( "Loading calibration for: " + file.getAbsolutePath() );
				
		if ( !file.exists() )
		{
			IOFunctions.println( "File '" + file + "' does not exist. Stopping." );
			return null;
		}

		final spim.fiji.spimdata.imgloaders.Calibration cal = LegacyStackImgLoaderLOCI.loadMetaData( file );

		if ( cal == null )
			return null;
		
		final double calX = cal.getCalX();
		final double calY = cal.getCalY();
		final double calZ = cal.getCalZ();

		return new Calibration( calX, calY, calZ );
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
