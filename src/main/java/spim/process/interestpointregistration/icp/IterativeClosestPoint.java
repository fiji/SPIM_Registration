package spim.process.interestpointregistration.icp;

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
	final String modelChoice[] = new String[] { "Translation", "Rigid", "Affine" };
	public static int defaultModel = 2;	
	protected int model = 2;
	
	protected IterativeClosestPointParameters parameters;

	public IterativeClosestPoint(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	protected IterativeClosestPointPairwise getPairwiseMatching( final ChannelInterestPointListPair pair, final String description)
	{
		return new IterativeClosestPointPairwise( pair, model, description, parameters );
	}

	@Override
	protected boolean runGlobalOpt(
			final GlobalOptimizationSubset subset, 
			final GlobalOptimizationType registrationType,
			final SpimData2 spimData,
			final ArrayList< ChannelProcess > channelsToProcess,
			final boolean considerTimePointsAsUnit )
	{
		if ( model == 0 )
			return subset.computeGlobalOpt( new TranslationModel3D(), registrationType, spimData, getChannelsToProcess(), getDescription(), considerTimePointsAsUnit );
		else if ( model == 1 )
			return subset.computeGlobalOpt( new RigidModel3D(), registrationType, spimData, getChannelsToProcess(), getDescription(), considerTimePointsAsUnit );
		else
			return subset.computeGlobalOpt( new AffineModel3D(), registrationType, spimData, getChannelsToProcess(), getDescription(), considerTimePointsAsUnit );	
	}

	@Override
	public void addQuery( final GenericDialog gd, final int registrationType )
	{
		gd.addChoice( "Transformation model", modelChoice, modelChoice[ defaultModel ] );
		gd.addSlider( "Maximal_distance for correspondence (px)", 0.25, 40.0, IterativeClosestPointParameters.maxDistance );
		gd.addNumericField( "Maximal_number of iterations", IterativeClosestPointParameters.maxIterations, 0 );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd, final int registrationType )
	{
		model = defaultModel = gd.getNextChoiceIndex();
		
		final double maxDistance = IterativeClosestPointParameters.maxDistance = gd.getNextNumber();
		final int maxIterations = IterativeClosestPointParameters.maxIterations = (int)Math.round( gd.getNextNumber() );
		
		this.parameters = new IterativeClosestPointParameters( maxDistance, maxIterations );
		
		return true;
	}

	@Override
	public InterestPointRegistration newInstance(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		return new IterativeClosestPoint( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Iterative closest-point (ICP, no invariance)";}
}
