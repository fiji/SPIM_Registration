package spim.process.interestpointregistration.icp;

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
import spim.fiji.plugin.interestpointregistration.PairwiseGloballyOptimalRegistration;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.TransformationModel;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;

/**
 * Iterative closest point implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IterativeClosestPoint extends PairwiseGloballyOptimalRegistration< IterativeClosestPointPairwise >
{
	public static int defaultModel = 2;	
	public static boolean defaultRegularize = false;
	protected TransformationModel model = null;

	protected IterativeClosestPointParameters parameters;

	public IterativeClosestPoint(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	protected IterativeClosestPointPairwise getPairwiseMatching( final ChannelInterestPointListPair pair, final String description)
	{
		return new IterativeClosestPointPairwise( pair, model, description, parameters );
	}

	@SuppressWarnings("unchecked")
	@Override
	protected boolean runGlobalOpt(
			final GlobalOptimizationSubset subset, 
			final GlobalOptimizationType registrationType,
			final SpimData2 spimData,
			final List< ChannelProcess > channelsToProcess,
			final boolean considerTimePointsAsUnit )
	{
		return subset.computeGlobalOpt( model.getModel(), registrationType, spimData, getChannelsToProcess(), getDescription() + ", " + model.getDescription(), getInitialTransformType(), considerTimePointsAsUnit );
	}

	@Override
	public void addQuery( final GenericDialog gd, final RegistrationType registrationType )
	{
		gd.addChoice( "Transformation model", TransformationModel.modelChoice, TransformationModel.modelChoice[ defaultModel ] );
		gd.addCheckbox( "Regularize_model", defaultRegularize );
		gd.addSlider( "Maximal_distance for correspondence (px)", 0.25, 40.0, IterativeClosestPointParameters.maxDistance );
		gd.addNumericField( "Maximal_number of iterations", IterativeClosestPointParameters.maxIterations, 0 );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd, final RegistrationType registrationType )
	{
		model = new TransformationModel( defaultModel = gd.getNextChoiceIndex() );
		
		if ( defaultRegularize = gd.getNextBoolean() )
		{
			if ( !model.queryRegularizedModel() )
				return false;
		}

		final double maxDistance = IterativeClosestPointParameters.maxDistance = gd.getNextNumber();
		final int maxIterations = IterativeClosestPointParameters.maxIterations = (int)Math.round( gd.getNextNumber() );
		
		this.parameters = new IterativeClosestPointParameters( maxDistance, maxIterations );
		
		return true;
	}

	@Override
	public InterestPointRegistration newInstance(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
	{
		return new IterativeClosestPoint( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Iterative closest-point (ICP, no invariance)";}
}
