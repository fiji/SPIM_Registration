package spim.process.interestpointregistration.geometrichashing;

public class GeometricHashingParameters
{
	public static float differenceThreshold = 50;
	public static float ratioOfDistance = 10;
	public static boolean useAssociatedBeads = false;

	protected final float dt, rod;
	protected final boolean ub;
	
	public GeometricHashingParameters()
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.ub = useAssociatedBeads;
	}
	
	public GeometricHashingParameters( final float differenceThreshold, final float ratioOfDistance, final boolean useAssociatedBeads )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.ub = useAssociatedBeads;
	}
	
	public float getDifferenceThreshold() { return dt; }
	public float getRatioOfDistance() { return rod; }
	public boolean getUseAssociatedBeads() { return ub; }
}
