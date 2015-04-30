package test;

import org.junit.Test;
import spim.fiji.plugin.Interest_Point_Detection;

/**
 * Created by moon on 4/28/15.
 */
public class InterestPointDetectionTest extends XmlDatasetTest
{
//	@Before
//	public void GenerateXMLDataset()
//	{
//		String xmlFile = "/Users/moon/temp/moon/test.xml";
//		File cziFile = new File("/Users/moon/temp/moon/2015-02-21_LZ1_Stock68_3.czi");
//
//		LightSheetZ1 z1 = new LightSheetZ1();
//		z1.defaultProcess( cziFile, xmlFile );
//		Toggle_Cluster_Options.displayClusterProcessing = false;
//	}

	@Test
	public void TestDifferenceOfMean()
	{
		Interest_Point_Detection ipd = new Interest_Point_Detection();
		ipd.defaultProcessUsingDifferenceOfMean( xmlFileName );
	}

	@Test
	public void TestDifferenceOfGaussian()
	{
		Interest_Point_Detection ipd = new Interest_Point_Detection();
		ipd.defaultProcessUsingDifferenceOfGaussian( xmlFileName );
	}
}
