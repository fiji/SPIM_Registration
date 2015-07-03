package spim.headless.registration.icp;

import mpicbg.models.Model;

public class IterativeClosestPointParameters
{
	public IterativeClosestPointParameters( final Model< ? > model )
	{
		this.model = model;
	}

	public static double maxDistance;

	protected double d = 5;
	protected int maxIt = 100;

	private Model< ? > model = null;
	public Model< ? > getModel() { return model.copy(); }

	public double getMaxDistance() { return d; }
	public int getMaxNumIterations() { return maxIt; }
}
