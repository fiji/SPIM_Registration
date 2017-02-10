package spim.process.interestpointregistration.pairwise.constellation;

import java.util.ArrayList;
import java.util.HashSet;
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

	public List< Pair< V, V > > getPairs() { return pairs; }
	public Set< V > getContainedViews() { return containedViews; }
	public Set< Set< V > > getGroups() { return groups; }
	public Set< V > getFixedViews() { return fixedViews; }

	public ArrayList< Pair< V, V > > fixViews( final List< V > fixedViews ) { return fixViews( this, fixedViews ); }

	protected void setFixedViews( final Set< V > fixedViews ) { this.fixedViews = fixedViews; }

	/**
	 * Fix an additional list of views (removes them from pairs and subsets)
	 * 
	 * @param fixedViews
	 */
	public static < V > ArrayList< Pair< V, V > > fixViews(
			final Subset< V > subset,
			final List< V > fixedViews )
	{
		final ArrayList< Pair< V, V > > removed = new ArrayList<>();

		// which of the fixed views are present in this subset?
		final HashSet< V > fixedSubsetViews = new HashSet<>();

		for ( final V fixedView : fixedViews )
			if ( subset.getContainedViews().contains( fixedView ) )
				fixedSubsetViews.add( fixedView );

		// add those that might be there already
		fixedSubsetViews.addAll( subset.getFixedViews() );
		subset.setFixedViews( fixedSubsetViews );

		// remove pairwise comparisons between two fixed views
		for ( int i = subset.getPairs().size() - 1; i >= 0; --i )
		{
			final Pair< V, V > pair = subset.getPairs().get( i );

			// remove a pair if both views are fixed
			if ( fixedSubsetViews.contains( pair.getA() ) && fixedSubsetViews.contains( pair.getB() ) )
			{
				subset.getPairs().remove( i );
				removed.add( pair );
			}
		}

		// now check if any of the fixed views is part of a group
		// if so, no checks between groups where each contains at 
		// least one fixed tile are necessary
		final ArrayList< Set< V > > groupsWithFixedViews = new ArrayList<>();

		for ( final Set< V > group : subset.getGroups() )
		{
			for ( final V fixedView : fixedSubsetViews )
				if ( group.contains( fixedView ) )
				{
					groupsWithFixedViews.add( group );
					break;
				}
		}

		// if there is more than one group containing fixed views,
		// we need to remove all pairs between them
		if ( groupsWithFixedViews.size() > 1 )
		{
			for ( int i = subset.getPairs().size() - 1; i >= 0; --i )
			{
				final Pair< V, V > pair = subset.getPairs().get( i );

				final V a = pair.getA();
				final V b = pair.getB();

				// if a and b are present in any combination of fixed groups
				// they do not need to be compared
				boolean aPresent = false;
				boolean bPresent = false;

				for ( final Set< V > fixedGroup : groupsWithFixedViews )
				{
					aPresent |= fixedGroup.contains( a );
					bPresent |= fixedGroup.contains( b );

					if ( aPresent && bPresent )
					{
						subset.getPairs().remove( i );
						removed.add( pair );
						break;
					}
				}
			}
		}

		return removed;
	}
}
