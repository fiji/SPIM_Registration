package spim.fiji.plugin.interestpointregistration.parameters;

import java.util.HashMap;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.interestpointregistration.pairwise.PairwiseGUI;

public class BasicRegistrationParameters
{
	public static String[] registrationTypes = {
			"Register timepoints individually", 
			"Match against one reference timepoint (no global optimization)", 
			"All-to-all timepoints matching (global optimization)", 
			"All-to-all timepoints matching with range ('reasonable' global optimization)" };

	public enum RegistrationType { TIMEPOINTS_INDIVIDUALLY, TO_REFERENCE_TIMEPOINT, ALL_TO_ALL, ALL_TO_ALL_WITH_RANGE };

	public PairwiseGUI pwr;
	public RegistrationType registrationType;
	public HashMap< ViewId, String > labelMap;
}
