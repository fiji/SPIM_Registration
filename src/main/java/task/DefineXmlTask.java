package task;

import mpicbg.spim.io.IOFunctions;
import spim.fiji.datasetmanager.LightSheetZ1;

import java.io.File;

/**
 * Created by moon on 4/30/15.
 */
public class DefineXmlTask extends BaseTask
{
	final String inputFile;

	public DefineXmlTask(String inputFile, String xmlFileName)
	{
		super(xmlFileName);
		this.inputFile = inputFile;
	}

	public static void main(String[] argv)
	{
		//		String xmlFileName = "/Users/moon/temp/moon/test.xml";
		//		File cziFile = new File("/Users/moon/temp/moon/2015-02-21_LZ1_Stock68_3.czi");

		DefineXmlTask task = new DefineXmlTask( argv[0], argv[1] );
		task.importLightSheetZ1();
	}

	public void importLightSheetZ1()
	{
		IOFunctions.printIJLog = false;

		LightSheetZ1 z1 = new LightSheetZ1();
		z1.defaultProcess( inputFile, xmlFileName );
	}
}
