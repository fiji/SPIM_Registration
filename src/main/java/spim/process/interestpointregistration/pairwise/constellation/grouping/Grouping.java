package spim.process.interestpointregistration.pairwise.constellation.grouping;

public interface Grouping< V, X extends V >
{
	public X group( final Group< V > group );
}
