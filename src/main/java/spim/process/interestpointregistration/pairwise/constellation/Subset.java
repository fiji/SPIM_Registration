package spim.process.interestpointregistration.pairwise.constellation;

import java.util.List;
import java.util.Set;

import net.imglib2.util.Pair;

public class Subset< V >
{
	/**
	 *  all views contained in this subset
	 */
	Set< V > containedViews;

	/**
	 * all pairs that need to be compared in that group
	 */
	List< Pair< V, V > > pairs;

	/**
	 * all groups that are part of this subset
	 */
	Set< Set< V > > groups;

	public Subset(
			final Set< V > containedViews,
			final List< Pair< V, V > > pairs,
			final Set< Set< V > > groups )
	{
		this.containedViews = containedViews;
		this.pairs = pairs;
		this.groups = groups;
	}

	public List< Pair< V, V > > getPairs() { return pairs; }
}
