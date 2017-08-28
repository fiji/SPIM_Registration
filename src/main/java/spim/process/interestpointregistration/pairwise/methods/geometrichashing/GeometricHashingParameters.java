package spim.process.interestpointregistration.pairwise.methods.geometrichashing;

import mpicbg.models.Model;

public class GeometricHashingParameters
{
	public static float differenceThreshold = Float.MAX_VALUE;
	public static float ratioOfDistance = 10;

	protected final float dt, rod;
	private Model< ? > model = null;

	public GeometricHashingParameters( final Model< ? > model )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.model = model;
	}
	
	public GeometricHashingParameters( final Model< ? > model, final float differenceThreshold, final float ratioOfDistance )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.model = model;
	}

	public Model< ? > getModel() { return model.copy(); }
	public float getDifferenceThreshold() { return dt; }
	public float getRatioOfDistance() { return rod; }
}
