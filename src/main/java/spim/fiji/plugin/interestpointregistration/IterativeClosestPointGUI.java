package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.spimdata.SpimData2;
import spim.headless.registration.icp.IterativeClosestPointParameters;
import spim.process.interestpointregistration.pairwise.IterativeClosestPointPairwise;

/**
 * Iterative closest point implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IterativeClosestPointGUI extends InterestPointRegistrationGUI
{
	public static int defaultModel = 2;
	public static boolean defaultRegularize = true;
	protected TransformationModelGUI model = null;

	protected IterativeClosestPointParameters parameters;

	public IterativeClosestPointGUI(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcessGUI > channelsToProcess )
	{
		super( spimData, viewIdsToProcess, channelsToProcess );
	}

	@Override
	protected IterativeClosestPointPairwise pairwiseMatchingInstance()
	{
		IterativeClosestPointParameters ip = new IterativeClosestPointParameters( model.getModel() );
		return new IterativeClosestPointPairwise( ip );
	}

	@Override
	public void addQuery( final GenericDialog gd, final RegistrationType registrationType )
	{
		gd.addChoice( "Transformation model", TransformationModelGUI.modelChoice, TransformationModelGUI.modelChoice[ defaultModel ] );
		gd.addCheckbox( "Regularize_model", defaultRegularize );
		gd.addSlider( "Maximal_distance for correspondence (px)", 0.25, 40.0, IterativeClosestPointParameters.maxDistance );
		gd.addNumericField( "Maximal_number of iterations", IterativeClosestPointParameters.maxIterations, 0 );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd, final RegistrationType registrationType )
	{
		model = new TransformationModelGUI( defaultModel = gd.getNextChoiceIndex() );
		
		if ( defaultRegularize = gd.getNextBoolean() )
		{
			if ( !model.queryRegularizedModel() )
				return false;
		}

		final double maxDistance = IterativeClosestPointParameters.maxDistance = gd.getNextNumber();
		final int maxIterations = IterativeClosestPointParameters.maxIterations = (int)Math.round( gd.getNextNumber() );

		this.parameters = new IterativeClosestPointParameters( model.getModel(), maxDistance, maxIterations );

		return true;
	}

	@Override
	public IterativeClosestPointGUI newInstance(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcessGUI > channelsToProcess )
	{
		return new IterativeClosestPointGUI( spimData, viewIdsToProcess, channelsToProcess );
	}

	@Override
	public String getDescription() { return "Iterative closest-point (ICP, no invariance)";}
}
