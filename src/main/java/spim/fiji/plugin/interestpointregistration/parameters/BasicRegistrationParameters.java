package spim.fiji.plugin.interestpointregistration.parameters;

import java.util.HashMap;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.plugin.interestpointregistration.pairwise.PairwiseGUI;

public class BasicRegistrationParameters
{
	public PairwiseGUI pwr;
	public RegistrationType registrationType;
	public HashMap< ViewId, String > labelMap;
}
