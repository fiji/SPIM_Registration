package spim.fiji.plugin.interestpointregistration.parameters;

import java.util.HashMap;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.interestpointregistration.pairwise.PairwiseGUI;
import spim.process.interestpointregistration.pairwise.constellation.overlap.AllAgainstAllOverlap;
import spim.process.interestpointregistration.pairwise.constellation.overlap.OverlapDetection;
import spim.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;

public class BasicRegistrationParameters
{
	public static String[] registrationTypeChoices = {
			"Register timepoints individually", 
			"Match against one reference timepoint (no global optimization)", 
			"All-to-all timepoints matching (global optimization)", 
			"All-to-all timepoints matching with range ('reasonable' global optimization)" };

	public static String[] overlapChoices = {
			"Compare all views against each other",
			"Only compare overlapping views (according to current transformations)" };

	public enum RegistrationType { TIMEPOINTS_INDIVIDUALLY, TO_REFERENCE_TIMEPOINT, ALL_TO_ALL, ALL_TO_ALL_WITH_RANGE };
	public enum OverlapType { ALL_AGAINST_ALL, OVERLAPPING_ONLY };

	public PairwiseGUI pwr;
	public RegistrationType registrationType;
	public OverlapType overlapType;
	public HashMap< ViewId, String > labelMap;
	public boolean groupTiles;

	public OverlapDetection< ViewId > getOverlapDetection( final SpimData spimData )
	{
		if ( overlapType == OverlapType.ALL_AGAINST_ALL )
			return new AllAgainstAllOverlap<>();
		else
			return new SimpleBoundingBoxOverlap<>( spimData );
	}
}
