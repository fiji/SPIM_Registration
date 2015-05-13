package test;

import ij.IJ;
import ij.ImageJ;
import mpicbg.spim.io.IOFunctions;
import org.junit.Test;
import spim.fiji.datasetmanager.LightSheetZ1;
import spim.fiji.plugin.Define_Multi_View_Dataset;

/**
 * Created by moon on 4/28/15.
 */
public class DefineXmlTest
{
	String cziFile;
	String xmlFile = "/Users/moon/temp/moon/test.xml";

	public DefineXmlTest()
	{

	}

//	public DefineXmlTest(String cziFile, String xmlFile)
//	{
//		this.cziFile = cziFile;
//		this.xmlFile = xmlFile;
//
//		IOFunctions.printIJLog = false;
//	}

	@Test
	public void testIJMacroDefineDataset()
	{
		IJ.setDebugMode( true );
		String typeOfDataset = "Zeiss Lightsheet Z.1 Dataset (LOCI Bioformats)";

		IJ.run( "Define Multi-View Dataset",
					"type_of_dataset=[" + typeOfDataset + "] " +
					"xml_filename=[" + xmlFile + "] " +
					"first_czi=[" + "/Users/moon/temp/moon/2015-02-21_LZ1_Stock68_3.czi" + "]");
	}

	@Test
	public void testParamsForLightSheetZ1()
	{
		Define_Multi_View_Dataset.Parameters params
				= new Define_Multi_View_Dataset.Parameters();

		params.setTypeOfDataset( "Zeiss Lightsheet Z.1 Dataset (LOCI Bioformats)" );

		LightSheetZ1.Parameters cziParams = new LightSheetZ1.Parameters();

		cziParams.setXmlFilename( xmlFile );
		cziParams.setFirstFile( cziFile );

		params.setCziParameters( cziParams );

		new Define_Multi_View_Dataset().process( params );
	}

	@Test
	public void testParamsDefineDataset()
	{
		IJ.setDebugMode( true );
		String typeOfDataset = "Zeiss Lightsheet Z.1 Dataset (LOCI Bioformats)";

		Define_Multi_View_Dataset.Parameters params
				= new Define_Multi_View_Dataset.Parameters();

		params.setTypeOfDataset( typeOfDataset );
	}

	@Test
	public void LightSheetZ1Test()
	{
		LightSheetZ1 z1 = new LightSheetZ1();
		LightSheetZ1.Parameters params = new LightSheetZ1.Parameters();

		params.setXmlFilename( xmlFile );
		params.setFirstFile( cziFile );
		z1.process( params );
	}
}
