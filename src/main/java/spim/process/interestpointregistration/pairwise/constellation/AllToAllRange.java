package spim.process.interestpointregistration.pairwise.constellation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.process.interestpointregistration.pairwise.constellation.range.RangeComparator;

public class AllToAllRange< V extends Comparable< V > > extends PairwiseSetup< V >
{
	final RangeComparator< V > rangeComparator;

	public AllToAllRange(
			final List< V > views,
			final Collection< Collection< V > > groups,
			final RangeComparator< V > rangeComparator )
	{
		super( views, groups );
		this.rangeComparator = rangeComparator;
	}

	@Override
	public void definePairs()
	{
		this.pairs = allToAllRange( views, groups, rangeComparator );
	}

	public static < V > List< Pair< V, V > > allToAllRange(
			final List< ? extends V > views,
			final Collection< ? extends Collection< V > > groups,
			final RangeComparator< V > rangeComparator )
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
				if ( !oneSetContainsBoth( viewIdA, viewIdB, groups ) && rangeComparator.inRange( viewIdA, viewIdB ) )
					viewPairs.add( new ValuePair< V, V >( viewIdA, viewIdB ) );
			}

		return viewPairs;
	}

	@Override
	public List< V > getDefaultFixedViews() { return new ArrayList<>(); }
}
