package spim.process.interestpointregistration.geometricdescriptor;

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.RANSACParameters;
import spim.process.interestpointregistration.TransformationModel;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;

/**
 * Redundant Geometric Local Descriptor Matching (RGLDM)
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class RGLDM extends InterestPointRegistration< RGLDMPairwise >
{
	public static int defaultModel = 2;
	public static boolean defaultRegularize = false;
	protected TransformationModel model = null;

	protected RGLDMParameters parameters;
	protected RANSACParameters ransacParams;

	public RGLDM(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	protected RGLDMPairwise getPairwiseMatching( final ChannelInterestPointListPair pair, final String description )
	{
		return new RGLDMPairwise( pair, model, description, ransacParams, parameters );
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
		return subset.computeGlobalOpt( model.getModel(), registrationType, spimData, getChannelsToProcess(), getDescription() + ", " + model.getDescription(), considerTimePointsAsUnit );
	}

	@Override
	public RGLDM newInstance(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
	{
		return new RGLDM( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}


	@Override
	public String getDescription() { return "Redundant geometric local descriptor matching (translation invariant)";}

	@Override
	public void addQuery( final GenericDialog gd, final RegistrationType registrationType )
	{
		gd.addChoice( "Transformation model", TransformationModel.modelChoice, TransformationModel.modelChoice[ defaultModel ] );
		gd.addCheckbox( "Regularize_model", defaultRegularize );
		gd.addSlider( "Number_of_neighbors for the descriptors", 3, 10, RGLDMParameters.numNeighbors );
		gd.addSlider( "Redundancy for descriptor matching", 0, 10, RGLDMParameters.redundancy );		
		gd.addSlider( "Significance required for a descriptor match", 1.0, 10.0, RGLDMParameters.ratioOfDistance );
		gd.addSlider( "Allowed_error_for_RANSAC (px)", 0.5, 20.0, RANSACParameters.max_epsilon );
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

		final int numNeighbors = RGLDMParameters.numNeighbors = (int)Math.round( gd.getNextNumber() );
		final int redundancy = RGLDMParameters.redundancy = (int)Math.round( gd.getNextNumber() );
		final float significance = RGLDMParameters.ratioOfDistance = (float)gd.getNextNumber();
		final float maxEpsilon = RANSACParameters.max_epsilon = (float)gd.getNextNumber();
		
		this.parameters = new RGLDMParameters( RGLDMParameters.differenceThreshold, significance, numNeighbors, redundancy );
		this.ransacParams = new RANSACParameters( maxEpsilon, RANSACParameters.min_inlier_ratio, RANSACParameters.min_inlier_factor, RANSACParameters.num_iterations );
		
		return true;
	}
}
