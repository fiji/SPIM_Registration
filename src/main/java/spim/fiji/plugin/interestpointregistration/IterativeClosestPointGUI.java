package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
import spim.fiji.plugin.interestpointregistration.TransformationModel;
import spim.fiji.spimdata.SpimData2;
import spim.headless.registration.icp.IterativeClosestPointParameters;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.PairwiseMatch;

/**
 * Iterative closest point implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IterativeClosestPointGUI extends IterativeClosestPointPairwise
{
	public static int defaultModel = 2;
	public static boolean defaultRegularize = true;
	protected TransformationModel model = null;

	protected IterativeClosestPointParameters parameters;

	public IterativeClosestPointGUI(final IterativeClosestPointParameters ip)
	{
		super(ip);
	}

//	@Override
	protected IterativeClosestPointPairwise pairwiseMatchingInstance( final PairwiseMatch pair, final String description)
	{
		IterativeClosestPointParameters ip = new IterativeClosestPointParameters( model.getModel() );
		return new IterativeClosestPointPairwise( ip );
	}

//	@Override
	protected TransformationModel getTransformationModel() { return model; }

//	@Override
	public void addQuery( final GenericDialog gd, final RegistrationType registrationType )
	{
//		gd.addChoice( "Transformation model", TransformationModel.modelChoice, TransformationModel.modelChoice[ defaultModel ] );
//		gd.addCheckbox( "Regularize_model", defaultRegularize );
//		gd.addSlider( "Maximal_distance for correspondence (px)", 0.25, 40.0, IterativeClosestPointParameters.maxDistance );
//		gd.addNumericField( "Maximal_number of iterations", IterativeClosestPointParameters.maxIterations, 0 );
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

		final double maxDistance = IterativeClosestPointParameters.maxDistance = gd.getNextNumber();
		final int maxIterations = (int)Math.round( gd.getNextNumber() );
		
		this.parameters = new IterativeClosestPointParameters( model.getModel() );
		
		return true;
	}

//	@Override
	public IterativeClosestPointGUI newInstance(
			final IterativeClosestPointParameters ip )
	{
		return new IterativeClosestPointGUI( ip );
	}

//	@Override
	public String getDescription() { return "Iterative closest-point (ICP, no invariance)";}
}
