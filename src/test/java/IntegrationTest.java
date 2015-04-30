/**
 * Created by moon on 4/28/15.
 */

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import test.DefineXmlTest;
import test.InterestPointDetectionTest;

import java.io.File;

public class IntegrationTest
{
	String xmlFileName = "/Users/moon/temp/moon/test.xml";
	File cziFile = new File("/Users/moon/temp/moon/2015-02-21_LZ1_Stock68_3.czi");

//	@Before
//	public void setup()
//	{
//		new File(xmlFileName).delete();
//	}

	@Test
	public void TestDefineXmlTest()
	{
		DefineXmlTest test = new DefineXmlTest(cziFile, xmlFileName);

		test.LightSheetZ1Test();

		assertEquals( new File(xmlFileName).exists(), true );
	}

//	@Test
//	public void TestHdf5ResaveTest()
//	{
//		Hdf5ResaveTest test = new Hdf5ResaveTest();
//
//		test.ResaveHDF5Test();
//	}

	@Test
	public void TestInterestPointDetectionDifferenceOfMeanTest()
	{
		InterestPointDetectionTest test = new InterestPointDetectionTest();

		test.TestDifferenceOfMean();
	}

	@Test
	public void TestRegistrationTest()
	{

	}
}
