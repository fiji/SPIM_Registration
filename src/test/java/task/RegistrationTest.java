package task;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import org.junit.Test;
import spim.fiji.plugin.Interest_Point_Registration;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.queryXML.ParseQueryXML;
import spim.fiji.spimdata.SpimData2;

import java.util.ArrayList;
import java.util.List;

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
