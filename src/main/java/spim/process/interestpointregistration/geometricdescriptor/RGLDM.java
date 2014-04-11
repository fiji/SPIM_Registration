package spim.process.interestpointregistration.geometricdescriptor;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.models.AffineModel3D;
import mpicbg.models.RigidModel3D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
import spim.fiji.plugin.interestpointregistration.PairwiseGloballyOptimalRegistration;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.RANSACParameters;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;

/**
 * Redundant Geometric Local Descriptor Matching (RGLDM)
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class RGLDM extends PairwiseGloballyOptimalRegistration< RGLDMPairwise >
{
	final String modelChoice[] = new String[] { "Translation", "Rigid", "Affine" };
	public static int defaultModel = 2;	
	protected int model = 2;
	
	protected RGLDMParameters parameters;
	protected RANSACParameters ransacParams;

	public RGLDM(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	protected RGLDMPairwise getPairwiseMatching( final ChannelInterestPointListPair pair, final String description )
	{
		return new RGLDMPairwise( pair, model, description, ransacParams, parameters );
	}

	@Override
	protected void runGlobalOpt(
			final GlobalOptimizationSubset subset, 
			final GlobalOptimizationType registrationType,
			final SpimData2 spimData,
			final ArrayList< ChannelProcess > channelsToProcess )
	{
		if ( model == 0 )
			subset.computeGlobalOpt( new TranslationModel3D(), registrationType, spimData, getChannelsToProcess(), getDescription() );
		else if ( model == 1 )
			subset.computeGlobalOpt( new RigidModel3D(), registrationType, spimData, getChannelsToProcess(), getDescription() );
		else
			subset.computeGlobalOpt( new AffineModel3D(), registrationType, spimData, getChannelsToProcess(), getDescription() );	
	}

	@Override
	public InterestPointRegistration newInstance(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		return new RGLDM( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}


	@Override
	public String getDescription() { return "Redundant geometric local descriptor matching (translation invariant)";}

	@Override
	public void addQuery( final GenericDialog gd, final int registrationType )
	{
		gd.addChoice( "Transformation model", modelChoice, modelChoice[ defaultModel ] );
		gd.addSlider( "Number_of_neighbors for the descriptors", 3, 10, RGLDMParameters.numNeighbors );
		gd.addSlider( "Redundancy for descriptor matching", 0, 10, RGLDMParameters.redundancy );		
		gd.addSlider( "Significance required for a descriptor match", 1.0, 10.0, RGLDMParameters.ratioOfDistance );
		gd.addSlider( "Allowed_error_for_RANSAC (px)", 0.5, 20.0, RANSACParameters.max_epsilon );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd, final int registrationType )
	{
		model = defaultModel = gd.getNextChoiceIndex();
		
		final int numNeighbors = RGLDMParameters.numNeighbors = (int)Math.round( gd.getNextNumber() );
		final int redundancy = RGLDMParameters.redundancy = (int)Math.round( gd.getNextNumber() );
		final float significance = RGLDMParameters.ratioOfDistance = (float)gd.getNextNumber();
		final float maxEpsilon = RANSACParameters.max_epsilon = (float)gd.getNextNumber();
		
		this.parameters = new RGLDMParameters( RGLDMParameters.differenceThreshold, significance, numNeighbors, redundancy );
		this.ransacParams = new RANSACParameters( maxEpsilon, RANSACParameters.min_inlier_ratio, RANSACParameters.min_inlier_factor, RANSACParameters.num_iterations );
		
		return true;
	}
}
