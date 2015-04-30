package task;

import org.junit.Test;
import spim.fiji.plugin.Image_Fusion;
import spim.fiji.plugin.resave.Resave_HDF5;

/**
 * Created by moon on 4/28/15.
 */
public class FusionTest extends XmlDatasetTest
{
	@Test
	public void DeconvolveDataset()
	{
		// Bounding box definition
		int[] min = new int[] {183, 45, -690};
		int[] max = new int[] {910, 1926, 714};
		new Image_Fusion().defaultProcess( xmlFileName, min, max );
	}

	public static void main(String[] argv)
	{
		FusionTest test = new FusionTest();
		test.DeconvolveDataset();
	}
}
