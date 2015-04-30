package test;

import org.junit.Before;
import org.junit.Test;
import spim.fiji.datasetmanager.LightSheetZ1;
import spim.fiji.plugin.resave.*;

import java.io.File;

/**
 * Created by moon on 4/28/15.
 */
public class Hdf5ResaveTest extends XmlDatasetTest
{
	@Before
	public void GenerateXMLDataset()
	{
		String xmlFile = "/Users/moon/temp/moon/test.xml";
		File cziFile = new File("/Users/moon/temp/moon/2015-02-21_LZ1_Stock68_3.czi");

		LightSheetZ1 z1 = new LightSheetZ1();
		z1.defaultProcess( cziFile, xmlFile );
	}

	@Test
	public void ResaveHDF5Test()
	{
		new Resave_HDF5().defaultProcess( xmlFileName, false );
	}
}
