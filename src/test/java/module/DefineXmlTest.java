package module;

import org.junit.Assert;
import org.junit.Test;
import task.LightSheetZ1DefineXmlTask;

/**
 * Test class for creating xml dataset from CZI
 */
public class DefineXmlTest
{
	String cziFile = "/Users/moon/temp/moon/2015-02-21_LZ1_Stock68_3.czi";
	String xmlFile = "/Users/moon/temp/moon/test.xml";

	@Test
	public void LightSheetZ1Test()
	{
		LightSheetZ1DefineXmlTask z1 = new LightSheetZ1DefineXmlTask();
		LightSheetZ1DefineXmlTask.Parameters params = new LightSheetZ1DefineXmlTask.Parameters();

		params.setXmlFilename( xmlFile );
		params.setFirstFile( cziFile );
		z1.process( params );
		Assert.assertNotEquals( z1.getSpimData(), null );
	}

	@Test
	public void LightSheetZ1CliTest()
	{
		final String[] params = new String[] {
				"-Dxml_filename=/Users/moon/temp/moon/test.xml",
				"-Dfirst_czi=/Users/moon/temp/moon/2015-02-21_LZ1_Stock68_3.czi",
				"-Dangle_1=0",
				"-Dangle_2=72",
				"-Dangle_3=144",
				"-Dangle_4=216",
				"-Dangle_5=288",
				"-Dchannel_1=green",
				"-Dchannel_2=red",
				"-Dillumination_1=0",
				"-Drotation_around=X-Axis",
				"-Dpixel_distance_x=0.28590",
				"-Dpixel_distance_y=0.28590",
				"-Dpixel_distance_z=1.50000",
				"-Dpixel_unit=um"
		};

		LightSheetZ1DefineXmlTask z1 = new LightSheetZ1DefineXmlTask();
		z1.process( params );
		Assert.assertNotEquals( z1.getSpimData(), null );
	}
}
