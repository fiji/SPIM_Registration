package spim.process.interestpointregistration.pairwise.constellation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.process.interestpointregistration.pairwise.constellation.range.AllInRange;

public class AllToAll< V extends Comparable< V > > extends AllToAllRange< V >
{
	public AllToAll(
			final List< V > views,
			final Set< Set< V > > groups )
	{
		super( views, groups, new AllInRange<>() );
	}

	public static < V > List< Pair< V, V > > allToAll(
			final List< ? extends V > views,
			final Collection< ? extends Collection< V > > groups )
	{
		// all pairs that need to be compared
		final ArrayList< Pair< V, V > > viewPairs = new ArrayList< Pair< V, V >>();

		for ( int a = 0; a < views.size() - 1; ++a )
			for ( int b = a + 1; b < views.size(); ++b )
			{
				final V viewIdA = views.get( a );
				final V viewIdB = views.get( b );

				// only compare those to views if not both are fixed and not
				// part of the same group
				if ( !oneSetContainsBoth( viewIdA, viewIdB, groups ) )
					viewPairs.add( new ValuePair< V, V >( viewIdA, viewIdB ) );
			}

		return viewPairs;
	}

}
