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
		String cziFile = "/Users/moon/temp/moon/2015-02-21_LZ1_Stock68_3.czi";

		LightSheetZ1 z1 = new LightSheetZ1();
		LightSheetZ1.Parameters params = new LightSheetZ1.Parameters();

		params.setXmlFilename( xmlFile );
		params.setFirstFile( cziFile );
		z1.process( params );
	}

	@Test
	public void ResaveHDF5Test()
	{
		new Resave_HDF5().defaultProcess( xmlFileName, false );
	}
}
