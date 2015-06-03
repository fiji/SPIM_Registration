package test;

import org.junit.Test;
import spim.fiji.plugin.Interest_Point_Registration;

/**
 * Created by moon on 4/28/15.
 * It contains Registration based on detected points.
 */
public class RegistrationTest extends XmlDatasetTest
{
	@Test
	public void TestRegisterDatasetUsingInterestPoints()
	{
		// Models
		// - GeometricHashing
		// - RGLDM
		// - IterativeClosestPoint

		Interest_Point_Registration ipr = new Interest_Point_Registration();
		ipr.defaultProcess( xmlFileName );
	}
}
