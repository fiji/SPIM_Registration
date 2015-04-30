package task;

import mpicbg.spim.io.IOFunctions;
import org.junit.Test;
import spim.fiji.datasetmanager.LightSheetZ1;

import java.io.File;

/**
 * Created by moon on 4/28/15.
 */
public class DefineXmlTest
{
	File cziFile;
	String xmlFile;
	public DefineXmlTest(File cziFile, String xmlFile)
	{
		this.cziFile = cziFile;
		this.xmlFile = xmlFile;

		IOFunctions.printIJLog = false;
	}

	@Test
	public void LightSheetZ1Test()
	{
		LightSheetZ1 z1 = new LightSheetZ1();
		z1.defaultProcess( cziFile, xmlFile );
	}
}
