package spim.process.interestpointregistration.icp;

public class IterativeClosestPointParameters
{
	public static double maxDistance = 5; 
	public static int maxIterations = 100;

	final protected double d;
	final protected int maxIt;
	
	public IterativeClosestPointParameters()
	{
		this.d = maxDistance;
		this.maxIt = maxIterations;
	}
	
	public IterativeClosestPointParameters( final double maxDistance, final int maxIterations )
	{
		this.d = maxDistance;
		this.maxIt = maxIterations;
	}
	
	public double getMaxDistance() { return d; }
	public int getMaxNumIterations() { return maxIt; }
}
