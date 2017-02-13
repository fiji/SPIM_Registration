package spim.fiji.plugin.interestpointregistration.pairwise;

import java.util.List;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.MatcherPairwise;

public interface PairwiseGUI
{
	/**
	 * adds the questions this registration wants to ask
	 * 
	 * @param gd
	 */
	public void addQuery( final GenericDialog gd );
	
	/**
	 * queries the questions asked before
	 * 
	 * @param gd
	 * @return
	 */
	public boolean parseDialog( final GenericDialog gd );
	
	/**
	 * @return - a new instance without any special properties
	 */
	public PairwiseGUI newInstance();

	/**
	 * @return - to be displayed in the generic dialog
	 */
	public String getDescription();

	/**
	 * @return - the object that will perform a pairwise matching and can return a result
	 */
	public MatcherPairwise pairwiseMatchingInstance();
}
