package spim.process.interestpointregistration.pairwise.methods.geometrichashing;

import mpicbg.models.Model;

public class GeometricHashingParameters
{
	public static float differenceThreshold = 50;
	public static float ratioOfDistance = 10;
	public static boolean useAssociatedBeads = false;

	protected final float dt, rod;
	protected final boolean ub;
	private Model< ? > model = null;

	public GeometricHashingParameters( final Model< ? > model )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.ub = useAssociatedBeads;
		this.model = model;
	}
	
	public GeometricHashingParameters( final Model< ? > model, final float differenceThreshold, final float ratioOfDistance, final boolean useAssociatedBeads )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.ub = useAssociatedBeads;
		this.model = model;
	}

	public Model< ? > getModel() { return model.copy(); }
	public float getDifferenceThreshold() { return dt; }
	public float getRatioOfDistance() { return rod; }
	public boolean getUseAssociatedBeads() { return ub; }
}
