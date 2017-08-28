package spim.fiji.plugin.interestpointregistration.pairwise;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
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
		gd.addSlider( "Allowed_error_for_RANSAC (px)", 0.5, 100.0, RANSACParameters.max_epsilon );
		gd.addSlider( "Significance required for a descriptor match", 1.0, 20.0, GeometricHashingParameters.ratioOfDistance );
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

		final float maxEpsilon = RANSACParameters.max_epsilon = (float)gd.getNextNumber();
		final float ratioOfDistance = GeometricHashingParameters.ratioOfDistance = (float)gd.getNextNumber();

		this.ransacParams = new RANSACParameters( maxEpsilon, RANSACParameters.min_inlier_ratio, RANSACParameters.min_inlier_factor, RANSACParameters.num_iterations );
		this.ghParams = new GeometricHashingParameters( model.getModel(), GeometricHashingParameters.differenceThreshold, ratioOfDistance );

		return true;
	}

	@Override
	public TransformationModelGUI getMatchingModel() { return model; }

	@Override
	public double getMaxError() { return ransacParams.getMaxEpsilon(); }
}
