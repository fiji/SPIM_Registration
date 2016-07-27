package spim.process.interestpointregistration.centerofmass;

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.PairwiseMatch;
import spim.process.interestpointregistration.TransformationModel;

/**
 * Center of Mass (Avg/Median) implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class CenterOfMass extends InterestPointRegistration
{
	final static String[] centerChoice = new String[]{ "Average", "Median" };
	public static int defaultCenterChoice = 0;

	protected int centerType = 0;

	public CenterOfMass(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcess > channelsToProcess )
	{
		super( spimData, viewIdsToProcess, channelsToProcess );
	}

	@Override
	protected CenterOfMassPairwise pairwiseMatchingInstance( final PairwiseMatch pair, final String description )
	{
		return new CenterOfMassPairwise( pair, centerType, description );
	}

	@Override
	protected TransformationModel getTransformationModel() { return new TransformationModel( 0 ); }

	@Override
	public void addQuery( final GenericDialog gd, final RegistrationType registrationType )
	{
		gd.addChoice( "Type of Center Computation", centerChoice, centerChoice[ defaultCenterChoice ] );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd, final RegistrationType registrationType )
	{
		this.centerType = gd.getNextChoiceIndex();

		return true;
	}

	@Override
	public CenterOfMass newInstance(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcess > channelsToProcess )
	{
		return new CenterOfMass( spimData, viewIdsToProcess, channelsToProcess );
	}

	@Override
	public String getDescription() { return "Center of Mass (translation-invariant)"; }
}
