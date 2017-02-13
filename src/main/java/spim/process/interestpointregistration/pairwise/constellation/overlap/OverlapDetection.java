package spim.process.interestpointregistration.pairwise.constellation.overlap;

public interface OverlapDetection< V >
{
	public boolean overlaps( final V view1, final V view2 );
}
