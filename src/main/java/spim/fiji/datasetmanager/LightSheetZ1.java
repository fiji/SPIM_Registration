package spim.fiji.datasetmanager;

import spim.fiji.spimdata.SpimDataInterestPoints;

public class LightSheetZ1 implements MultiViewDatasetDefinition
{
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
	public SpimDataInterestPoints createDataset()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LightSheetZ1 newInstance() { return new LightSheetZ1(); }
}
