package spim.fiji.plugin.interestpointregistration.pairwise;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.interestpointregistration.TransformationModelGUI;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.pairwise.MatcherPairwise;
import spim.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;

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
	public MatcherPairwise< InterestPoint > pairwiseMatchingInstance();

	/**
	 * This is not good style, but when creating the object we do not know which generic parameter will be required
	 * as the user specifies this later (could be a factory)
	 * 
	 * @return - the object that will perform a pairwise matching and can return a result for grouped interestpoints
	 */
	public MatcherPairwise< GroupedInterestPoint< ViewId > > pairwiseGroupedMatchingInstance();

	/**
	 * @return - the model the user chose to perform the registration with
	 */
	public TransformationModelGUI getMatchingModel();
}
