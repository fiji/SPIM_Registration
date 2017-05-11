package spim.process.interestpointregistration.pairwise.constellation.overlap;

public class AllAgainstAllOverlap< V > implements OverlapDetection< V >
{
	@Override
	public boolean overlaps( final V view1, final V view2 ) { return true; }
}
