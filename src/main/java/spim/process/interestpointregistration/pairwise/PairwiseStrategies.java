package spim.process.interestpointregistration.pairwise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;

public class PairwiseStrategies
{
	public List< Pair< ViewId, ViewId > > allToAll(
			final List< ViewId > views,
			final Collection< ViewId > fixed,
			final Collection< Collection< ViewId > > groups )
	{
		// all pairs that need to be compared
		final ArrayList< Pair< ViewId, ViewId > > viewPairs = new ArrayList< Pair< ViewId, ViewId > >();

		for ( int a = 0; a < views.size() - 1; ++a )
			for ( int b = a + 1; b < views.size(); ++b )
			{
				final ViewId viewIdA = views.get( a );
				final ViewId viewIdB = views.get( b );

				// only compare those to views if not both are fixed and not part of the same group
				if ( validPair( viewIdA, viewIdB, fixed, groups ) )
					viewPairs.add( new ValuePair< ViewId, ViewId > ( viewIdA, viewIdB ) );
			}

		return viewPairs;
	}

	public static boolean validPair(
			final ViewId viewIdA,
			final ViewId viewIdB,
			final Collection< ViewId > fixed,
			final Collection< Collection< ViewId > > groups )
	{
		if ( fixed.contains( viewIdA ) && fixed.contains( viewIdB ) )
			return false;

		if ( oneSetContainsBoth( viewIdA, viewIdB, groups ) )
			return false;

		return true;
	}

	public static boolean oneSetContainsBoth( final ViewId viewIdA, final ViewId viewIdB, final Collection< Collection< ViewId > > sets )
	{
		for ( final Collection< ViewId > set : sets )
			if ( set.contains( viewIdA ) && set.contains( viewIdB ) )
				return true;

		return false;
	}
}
