package spim.process.interestpointregistration.geometricdescriptor;

public class RGLDMParameters
{
	public static float differenceThreshold = 50; 
	public static float ratioOfDistance = 3; 

	public static int numNeighbors = 3;
	public static int redundancy = 1;
	
	protected final float dt, rod;
	protected final int nn, re;
	
	public RGLDMParameters()
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.nn = numNeighbors;
		this.re = redundancy;
	}
	
	public RGLDMParameters( final float differenceThreshold, final float ratioOfDistance, final int numNeighbors, final int redundancy )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.nn = numNeighbors;
		this.re = redundancy;
	}
	
	public float getDifferenceThreshold() { return dt; }
	public float getRatioOfDistance() { return rod; }
	public int getNumNeighbors() { return nn; }
	public int getRedundancy() { return re; }
}
