package spim.process.interestpointregistration.geometrichashing3d;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.models.AffineModel3D;
import mpicbg.models.RigidModel3D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.plugin.interestpointregistration.PairwiseGloballyOptimalRegistration;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.RANSACParameters;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;

public class GeometricHashing3d extends PairwiseGloballyOptimalRegistration< GeometricHashing3dPairwise >
{
	final String modelChoice[] = new String[] { "Translation", "Rigid", "Affine" };
	public static int defaultModel = 2;	
	protected int model = 2;

	protected RANSACParameters ransacParams;

	public GeometricHashing3d(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	protected GeometricHashing3dPairwise getPairwiseMatching( final ChannelInterestPointListPair pair, final String description )
	{
		return new GeometricHashing3dPairwise( pair, model, description, ransacParams );
	}

	@Override
	protected void runGlobalOpt(final GlobalOptimizationSubset subset, 
			final GlobalOptimizationType registrationType,
			final SpimData2 spimData,
			final ArrayList< ChannelProcess > channelsToProcess,
			final boolean considerTimePointsAsUnit )
	{
		if ( model == 0 )
			subset.computeGlobalOpt( new TranslationModel3D(), registrationType, spimData, getChannelsToProcess(), getDescription(), considerTimePointsAsUnit );
		else if ( model == 1 )
			subset.computeGlobalOpt( new RigidModel3D(), registrationType, spimData, getChannelsToProcess(), getDescription(), considerTimePointsAsUnit );
		else
			subset.computeGlobalOpt( new AffineModel3D(), registrationType, spimData, getChannelsToProcess(), getDescription(), considerTimePointsAsUnit );	
	}

	@Override
	public GeometricHashing3d newInstance(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		return new GeometricHashing3d( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Fast 3d geometric hashing (rotation invariant)";}

	@Override
	public void addQuery( final GenericDialog gd, final int registrationType )
	{
		gd.addChoice( "Transformation model", modelChoice, modelChoice[ defaultModel ] );
		gd.addSlider( "Allowed_error_for_RANSAC (px)", 0.5, 20.0, RANSACParameters.max_epsilon );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd, final int registrationType )
	{
		model = defaultModel = gd.getNextChoiceIndex();
		final float maxEpsilon = RANSACParameters.max_epsilon = (float)gd.getNextNumber();
		
		this.ransacParams = new RANSACParameters( maxEpsilon, RANSACParameters.min_inlier_ratio, RANSACParameters.min_inlier_factor, RANSACParameters.num_iterations );

		return true;
	}
}
