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
	Set< V > views;

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
			final Set< V > views,
			final List< Pair< V, V > > pairs,
			final Set< Set< V > > groups )
	{
		this.views = views;
		this.pairs = pairs;
		this.groups = groups;
	}

	public List< Pair< V, V > > getPairs() { return pairs; }
	public Set< V > getViews() { return views; }
	public Set< Set< V > > getGroups() { return groups; }
	public Set< V > getFixedViews() { return fixedViews; }
	protected void setFixedViews( final Set< V > fixedViews ) { this.fixedViews = fixedViews; }

	/**
	 * Fix an additional list of views (removes pairs from subsets and sets list of fixed views)
	 * 
	 * @param fixedViews
	 * @return - the removed pairs due to fixed views
	 */
	public ArrayList< Pair< V, V > > fixViews( final List< V > fixedViews )
	{
		// which fixed views are actually present in this subset?
		final HashSet< V > fixedSubsetViews = filterPresentViews( views, fixedViews );

		// add the currently fixed ones
		fixedSubsetViews.addAll( getFixedViews() );

		// store the currently fixed views
		setFixedViews( fixedSubsetViews );

		return fixViews( fixedSubsetViews, pairs, groups );
	}

	/**
	 * Computes a list of grouped views that need to be compared, expressed as pairs.
	 * Single views are expressed as groups with cardinality 1. Then idea is that for
	 * pairwise comparisons, the algorithm groups views (e.g. all interestpoints) before
	 * running the actual algorithm.
	 */
	public List< Pair< Set< V >, Set< V > > > getGroupedPairs()
	{
		// all views contained in groups
		final Set< Set< V > > groups = createGroupsForAllViews( views, this.groups );

		for ( final Pair< V, V > pair : pairs )
		{
			
		}

		return null;
	}

	/**
	 * Create a Set of groups that contains all views, with |group| >= 1.
	 * 
	 * @param views
	 * @param groups
	 * @return
	 */
	public static < V > Set< Set< V > > createGroupsForAllViews(
			final Set< V > views,
			final Set< Set< V > > groups )
	{
		final Set< Set< V > > groupsFull = new HashSet<>();

		groupsFull.addAll( groups );

		for ( final V view : views )
		{
			// check if the view is contained in any of the groups
			boolean contains = false;

			for ( final Set< V > group : groups )
			{
				if ( group.contains( view ) )
				{
					contains = true;
					break;
				}
			}

			// if the view is not contained in any group, make a new one
			if ( !contains )
			{
				final HashSet< V > group = new HashSet<>();
				group.add( view );
				groupsFull.add( group );
			}
		}

		return groupsFull;
	}

	/**
	 * Checks which fixed views are present in the views list and puts them into a HashMap
	 * 
	 * @param views
	 * @param fixedViews
	 * @return
	 */
	public static < V > HashSet< V > filterPresentViews( final Set< V > views, final List< V > fixedViews )
	{
		// which of the fixed views are present in this subset?
		final HashSet< V > fixedSubsetViews = new HashSet<>();

		for ( final V fixedView : fixedViews )
				fixedSubsetViews.add( fixedView );

		return fixedSubsetViews;
	}

	/**
	 * Fix an additional list of views (removes pairs from subsets and sets list of fixed views)
	 * 
	 * @param subset
	 * @param fixedViews
	 */
	public static < V > ArrayList< Pair< V, V > > fixViews(
			final HashSet< V > fixedSubsetViews,
			final List< Pair< V, V > > pairs,
			final Set< Set< V > > groups )
	{
		final ArrayList< Pair< V, V > > removed = new ArrayList<>();

		// remove pairwise comparisons between two fixed views
		for ( int i = pairs.size() - 1; i >= 0; --i )
		{
			final Pair< V, V > pair = pairs.get( i );

			// remove a pair if both views are fixed
			if ( fixedSubsetViews.contains( pair.getA() ) && fixedSubsetViews.contains( pair.getB() ) )
			{
				pairs.remove( i );
				removed.add( pair );
			}
		}

		// now check if any of the fixed views is part of a group
		// if so, no checks between groups where each contains at 
		// least one fixed tile are necessary
		final ArrayList< Set< V > > groupsWithFixedViews = new ArrayList<>();

		for ( final Set< V > group : groups )
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
			for ( int i = pairs.size() - 1; i >= 0; --i )
			{
				final Pair< V, V > pair = pairs.get( i );

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
						pairs.remove( i );
						removed.add( pair );
						break;
					}
				}
			}
		}

		return removed;
	}
}
