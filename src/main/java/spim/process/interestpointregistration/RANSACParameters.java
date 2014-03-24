package spim.process.interestpointregistration;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class RANSACParameters
{
    public static float max_epsilon = 5;
    public static float min_inlier_ratio = 0.1f;
    public static int num_iterations = 1000;
    public static float min_inlier_factor = 3f;

    final protected float maxEpsilon, minInlierRatio, minInlierFactor;
    final protected int numIterations;
    
    public RANSACParameters( final float maxEpsilon, final float minInlierRatio, final float minInlierFactor, final int numIterations )
    {
    	this.maxEpsilon = maxEpsilon;
    	this.minInlierRatio = minInlierRatio;
    	this.minInlierFactor = minInlierFactor;
    	this.numIterations = numIterations;
    }
    
    public RANSACParameters()
    {
    	this.maxEpsilon = max_epsilon;
    	this.numIterations = num_iterations;
    	this.minInlierRatio = min_inlier_ratio;
    	this.minInlierFactor = min_inlier_factor;
    }
    
    public float getMaxEpsilon() { return maxEpsilon; }
    public float getMinInlierRatio() { return minInlierRatio; }
    public float getMinInlierFactor() { return minInlierFactor; }
    public int getNumIterations() { return numIterations; }
}
