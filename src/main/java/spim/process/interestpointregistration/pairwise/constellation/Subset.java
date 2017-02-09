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

	/**
	 * all views in this subset that are fixed
	 */
	Set< V > fixedViews;
	
	public Subset(
			final Set< V > containedViews,
			final List< Pair< V, V > > pairs,
			final Set< Set< V > > groups )
	{
		this.containedViews = containedViews;
		this.pairs = pairs;
		this.groups = groups;
	}

	public void setFixedViews( final Set< V > fixedViews ) { this.fixedViews = fixedViews; }

	public List< Pair< V, V > > getPairs() { return pairs; }
	public Set< V > getContainedViews() { return containedViews; }
	public Set< Set< V > > getGroups() { return groups; }
	public Set< V > getFixedViews() { return fixedViews; }
}
