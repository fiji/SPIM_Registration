package spim.process.interestpointregistration.pairwise.constellation;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.imglib2.util.Pair;
import spim.process.interestpointregistration.pairwise.constellation.range.AllInRange;

public class AllToAll< V extends Comparable< V > > extends AllToAllRange< V, AllInRange< V > >
{
	public AllToAll(
			final List< V > views,
			final Set< Set< V > > groups )
	{
		super( views, groups, new AllInRange<>() );
	}

	public static < V > List< Pair< V, V > > allPairs(
			final List< ? extends V > views,
			final Collection< ? extends Collection< V > > groups )
	{
		return AllToAllRange.allPairs( views, groups, new AllInRange<>() );
	}
}
