package spim.process.interestpointregistration.pairwise;

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.plugin.interestpointregistration.TransformationModel;
import spim.fiji.spimdata.SpimData2;
import spim.headless.registration.RANSACParameters;
import spim.headless.registration.geometrichashing.GeometricHashingParameters;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.PairwiseMatch;

public class GeometricHashingGUI extends GeometricHashingPairwise
{
	public static int defaultModel = 2;
	public static boolean defaultRegularize = true;
	protected TransformationModel model = null;

	protected RANSACParameters ransacParams;
	protected GeometricHashingParameters ghParams;

	public GeometricHashingGUI(
			final RANSACParameters rp,
			final GeometricHashingParameters gp )
	{
		super(rp, gp);
	}

//	@Override
	protected GeometricHashingPairwise pairwiseMatchingInstance( final PairwiseMatch pair, final String description )
	{
		return new GeometricHashingPairwise( ransacParams, ghParams );
	}

//	@Override
	protected TransformationModel getTransformationModel() { return model; }

//	@Override
	public GeometricHashingGUI newInstance(
			final RANSACParameters rp,
			final GeometricHashingParameters gp  )
	{
		return new GeometricHashingGUI( rp, gp );
	}

//	@Override
	public String getDescription() { return "Fast 3d geometric hashing (rotation invariant)";}

//	@Override
	public void addQuery( final GenericDialog gd, final RegistrationType registrationType )
	{
		gd.addChoice( "Transformation model", TransformationModel.modelChoice, TransformationModel.modelChoice[ defaultModel ] );
		gd.addCheckbox( "Regularize_model", defaultRegularize );
		gd.addSlider( "Allowed_error_for_RANSAC (px)", 0.5, 20.0, RANSACParameters.max_epsilon );
		gd.addSlider( "Significance required for a descriptor match", 1.0, 20.0, GeometricHashingParameters.ratioOfDistance );
	}

//	@Override
	public boolean parseDialog( final GenericDialog gd, final RegistrationType registrationType )
	{
		model = new TransformationModel( defaultModel = gd.getNextChoiceIndex() );
		
		if ( defaultRegularize = gd.getNextBoolean() )
		{
			if ( !model.queryRegularizedModel() )
				return false;
		}

		final float maxEpsilon = RANSACParameters.max_epsilon = (float)gd.getNextNumber();
		final float ratioOfDistance = GeometricHashingParameters.ratioOfDistance = (float)gd.getNextNumber();

		this.ransacParams = new RANSACParameters( maxEpsilon, RANSACParameters.min_inlier_ratio, RANSACParameters.min_inlier_factor, RANSACParameters.num_iterations );
		this.ghParams = new GeometricHashingParameters( model.getModel(), GeometricHashingParameters.differenceThreshold, ratioOfDistance, GeometricHashingParameters.useAssociatedBeads );

		return true;
	}
}
