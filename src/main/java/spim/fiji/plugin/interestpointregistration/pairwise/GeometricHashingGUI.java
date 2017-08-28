package spim.fiji.plugin.interestpointregistration.pairwise;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.interestpointregistration.TransformationModelGUI;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.pairwise.MatcherPairwise;
import spim.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import spim.process.interestpointregistration.pairwise.methods.geometrichashing.GeometricHashingPairwise;
import spim.process.interestpointregistration.pairwise.methods.geometrichashing.GeometricHashingParameters;
import spim.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;

public class GeometricHashingGUI implements PairwiseGUI
{
	public static int defaultModel = 2;
	public static boolean defaultRegularize = true;
	public static int defaultRANSACIterationChoice = 1;
	protected TransformationModelGUI model = null;

	protected RANSACParameters ransacParams;
	protected GeometricHashingParameters ghParams;

	@Override
	public GeometricHashingPairwise< InterestPoint > pairwiseMatchingInstance()
	{
		return new GeometricHashingPairwise< InterestPoint >( ransacParams, ghParams );
	}

	@Override
	public MatcherPairwise< GroupedInterestPoint< ViewId > > pairwiseGroupedMatchingInstance()
	{
		return new GeometricHashingPairwise< GroupedInterestPoint< ViewId > >( ransacParams, ghParams );
	}

	@Override
	public GeometricHashingGUI newInstance() { return new GeometricHashingGUI(); }

	@Override
	public String getDescription() { return "Fast 3d geometric hashing (rotation invariant)";}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		gd.addChoice( "Transformation model", TransformationModelGUI.modelChoice, TransformationModelGUI.modelChoice[ defaultModel ] );
		gd.addCheckbox( "Regularize_model", defaultRegularize );
		gd.addSlider( "Significance required for a descriptor match", 1.0, 20.0, GeometricHashingParameters.ratioOfDistance );
		gd.addMessage( "" );
		gd.addSlider( "Allowed_error_for_RANSAC (px)", 0.5, 100.0, RANSACParameters.max_epsilon );
		gd.addChoice( "Number_of_RANSAC_iterations", RANSACParameters.ransacChoices, RANSACParameters.ransacChoices[ defaultRANSACIterationChoice ] );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		model = new TransformationModelGUI( defaultModel = gd.getNextChoiceIndex() );
		
		if ( defaultRegularize = gd.getNextBoolean() )
		{
			if ( !model.queryRegularizedModel() )
				return false;
		}

		final float ratioOfDistance = GeometricHashingParameters.ratioOfDistance = (float)gd.getNextNumber();
		final float maxEpsilon = RANSACParameters.max_epsilon = (float)gd.getNextNumber();
		final int ransacIterations = RANSACParameters.ransacChoicesIterations[ defaultRANSACIterationChoice = gd.getNextChoiceIndex() ];

		final float minInlierRatio;
		if ( ratioOfDistance >= 2 )
			minInlierRatio = RANSACParameters.min_inlier_ratio;
		else if ( ratioOfDistance >= 1.5 )
			minInlierRatio = RANSACParameters.min_inlier_ratio / 10;
		else
			minInlierRatio = RANSACParameters.min_inlier_ratio / 100;

		this.ghParams = new GeometricHashingParameters( model.getModel(), GeometricHashingParameters.differenceThreshold, ratioOfDistance );
		this.ransacParams = new RANSACParameters( maxEpsilon, minInlierRatio, RANSACParameters.min_inlier_factor, ransacIterations );

		IOFunctions.println( "Selected Paramters:" );
		IOFunctions.println( "model: " + defaultModel );
		IOFunctions.println( "ratioOfDistance: " + ratioOfDistance );
		IOFunctions.println( "maxEpsilon: " + maxEpsilon );
		IOFunctions.println( "ransacIterations: " + ransacIterations );
		IOFunctions.println( "minInlierRatio: " + minInlierRatio );

		return true;
	}

	@Override
	public TransformationModelGUI getMatchingModel() { return model; }

	@Override
	public double getMaxError() { return ransacParams.getMaxEpsilon(); }
}
