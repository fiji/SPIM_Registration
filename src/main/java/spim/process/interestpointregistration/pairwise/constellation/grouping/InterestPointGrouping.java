package spim.process.interestpointregistration.pairwise.constellation.grouping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public abstract class InterestPointGrouping< V extends ViewId >
{
	public void group( final Group< V > group, final Map< V, List< InterestPoint > > interestpoints )
	{
		final ArrayList< List< InterestPoint > > toMerge = new ArrayList<>();

		for ( final V view : group )
		{
			final List< InterestPoint > points = interestpoints.get( view );

			if ( points == null )
				throw new RuntimeException( "no interestpoints available" );

			toMerge.add( points );
		}
	}

	public abstract void merge( ArrayList< List< InterestPoint > > toMerge );
}
