package spim.process.interestpointregistration.pairwise.constellation.grouping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public abstract class InterestPointGrouping< V extends ViewId > implements Grouping< V, List< GroupedInterestPoint< V > > >
{
	// all interestpoints
	final Map< V, List< InterestPoint > > interestpoints;

	public InterestPointGrouping( final Map< V, List< InterestPoint > > interestpoints )
	{
		this.interestpoints = interestpoints;
	}

	@Override
	public List< GroupedInterestPoint< V > > group( final Group< V > group )
	{
		final Map< V, List< InterestPoint > > toMerge = new HashMap<>();

		for ( final V view : group )
		{
			final List< InterestPoint > points = interestpoints.get( view );

			if ( points == null )
				throw new RuntimeException( "no interestpoints available" );

			toMerge.put( view, points );
		}

		return merge( toMerge );
	}

	protected abstract List< GroupedInterestPoint< V > > merge( Map< V, List< InterestPoint > > toMerge );
}
