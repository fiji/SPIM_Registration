package task;

import spim.fiji.plugin.Image_Fusion;

/**
 * Created by moon on 4/30/15.
 */
public class FusionTask
{
	public static void main(String[] argv)
	{
		String xmlFileName = "/projects/pilot_spim/moon/test.xml";
		// Bounding box definition
		int[] min = new int[] {183, 45, -690};
		int[] max = new int[] {910, 1926, 714};
		new Image_Fusion().defaultProcess( xmlFileName, min, max );
	}
}
