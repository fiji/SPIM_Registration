package spim.fiji.plugin.interestpointregistration.pairwise;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.interestpointregistration.TransformationModelGUI;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.pairwise.MatcherPairwise;
import spim.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import spim.process.interestpointregistration.pairwise.methods.centerofmass.CenterOfMassPairwise;
import spim.process.interestpointregistration.pairwise.methods.centerofmass.CenterOfMassParameters;
import spim.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;

/**
 * Center of mass GUI
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class CenterOfMassGUI implements PairwiseGUI
{
	final static String[] centerChoice = new String[]{ "Average", "Median" };
	public static int defaultCenterChoice = 0;

	protected int centerType = 0;

	@Override
	public CenterOfMassPairwise< InterestPoint > pairwiseMatchingInstance()
	{
		return new CenterOfMassPairwise< InterestPoint >( new CenterOfMassParameters( centerType ) );
	}

	@Override
	public MatcherPairwise< GroupedInterestPoint< ViewId > > pairwiseGroupedMatchingInstance()
	{
		return new CenterOfMassPairwise< GroupedInterestPoint< ViewId > >( new CenterOfMassParameters( centerType ) );
	}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		gd.addChoice( "Type of Center Computation", centerChoice, centerChoice[ defaultCenterChoice ] );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		this.centerType = defaultCenterChoice = gd.getNextChoiceIndex();

		return true;
	}

	@Override
	public CenterOfMassGUI newInstance() { return new CenterOfMassGUI(); }

	@Override
	public String getDescription() { return "Center of Mass (translation invariant)";}

	@Override
	public TransformationModelGUI getMatchingModel() { return new TransformationModelGUI( 0 ); }

	@Override
	public double getMaxError() { return Double.NaN; }
}
