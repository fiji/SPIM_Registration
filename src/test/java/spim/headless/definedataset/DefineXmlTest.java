package spim.headless.definedataset;

import org.junit.Assert;
import org.junit.Test;
import spim.fiji.spimdata.SpimData2;

import java.io.File;

/**
 * DefineXml Test
 */
public class DefineXmlTest
{
	@Test
	public void LightSheetZ1Test()
	{
		String cziFile = "/Users/moon/temp/moon/2015-02-21_LZ1_Stock68_3.czi";
		String xmlFile = "/Users/moon/temp/moon/test.xml";

		DefineDataSetParameters params = new DefineDataSetParameters();

		SpimData2 spimData = LightSheetZ1.createDataset( cziFile, params );

		SpimData2.saveXML( spimData, xmlFile, "" );

		Assert.assertTrue( new File(xmlFile).exists() );
	}

	@Test
	public void MicroManagerTest()
	{
		String tiffFile = "/Users/moon/temp/moon/test.tiff";
		String xmlFile = "/Users/moon/temp/moon/test.xml";

		DefineDataSetParameters params = new DefineDataSetParameters();

		SpimData2 spimData = MicroManager.createDataset( tiffFile, params );

		SpimData2.saveXML( spimData, xmlFile, "" );

		Assert.assertTrue( new File(xmlFile).exists() );
	}

	@Test
	public void StackListImageJTest()
	{

	}

	@Test
	public void StackListLOCI()
	{

	}
}
