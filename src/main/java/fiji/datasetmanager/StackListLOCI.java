package fiji.datasetmanager;

import mpicbg.spim.data.SpimData;

public class StackListLOCI implements MultiViewDatasetDefinition
{
	@Override
	public String getTitle() 
	{
		return "3D_Image_Stacks_(LOCI Bioformats)";
	}

	@Override
	public SpimData<?, ?> createDataset()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getExtendedDescription()
	{
		return "This dataset definition supports a series of three-dimensional image\n" +
			   "stacks, all present in one folder. The filename of each file should\n" +
			   "encode timepoint, angle, channel, illumination direction if applicable.\n" +
			   "The 3d image stacks can be of any fileformat that LOCI Bioformats is\n" +
			   "able to read, for example TIFF, LSM, CZI, ...\n"+
			   "\nFor example, the 3d image stack files could could be named:\n" +
			   "spim_TL1_Channel3_Illum1_Angle45.tif or data_TP01_Angle045.lsm";
	}

}
