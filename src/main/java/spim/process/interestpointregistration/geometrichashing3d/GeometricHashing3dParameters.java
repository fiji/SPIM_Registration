package spim.process.interestpointregistration.geometrichashing3d;

public class GeometricHashing3dParameters
{
	public static float differenceThreshold = 50; 
	public static float ratioOfDistance = 10; 
	public static boolean useAssociatedBeads = false;

	protected final float dt, rod;
	protected final boolean ub;
	
	public GeometricHashing3dParameters()
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.ub = useAssociatedBeads;
	}
	
	public GeometricHashing3dParameters( final float differenceThreshold, final float ratioOfDistance, final boolean useAssociatedBeads )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.ub = useAssociatedBeads;
	}
	
	public float getDifferenceThreshold() { return dt; }
	public float getRatioOfDistance() { return rod; }
	public boolean getUseAssociatedBeads() { return ub; }
}
