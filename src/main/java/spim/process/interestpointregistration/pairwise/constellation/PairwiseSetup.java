package spim.process.interestpointregistration.pairwise.constellation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public abstract class PairwiseSetup< V extends Comparable< V > >
{
	protected List< V > views;
	protected Set< Set< V > > groups;
	protected List< Pair< V, V > > pairs;
	protected ArrayList< ArrayList< Pair< V, V > > > subsets;

	/**
	 * Sets up all pairwise comparisons
	 * 
	 * 1) definePairs()
	 * 2) reorderPairs() - if wanted
	 * 3) detectSubsets()
	 * 4) sortSubsets() - if wanted
	 * 5) fixViews( getDefaultFixedViews() )
	 * 6) fixViews() - fixed some of the views necessary for the strategy to work
	 * 
	 * @param views
	 * @param groups
	 */
	public PairwiseSetup( final List< V > views, final Set< Set< V > > groups )
	{
		this.views = views;
		this.groups = groups;
	}

	public PairwiseSetup( final List< V > views ) { this( views, null ); }

	public List< V > getViews() { return views; }
	public void setViews( final List< V > views ) { this.views = views; }

	public Set< Set< V > > getGroups() { return groups; }
	public void setGroups( final Set< Set< V > > groups ) { this.groups = groups; }

	public List< Pair< V, V > > getPairs() { return pairs; }
	public void setPairs( final List< Pair< V, V > > pairs ) { this.pairs = pairs; }

	public ArrayList< ArrayList< Pair< V, V > > > getSubsets() { return subsets; }
	public void setSubsets( final ArrayList< ArrayList< Pair< V, V > > > subsets ) { this.subsets = subsets; }

	/**
	 * Given a list of views and their grouping, identify all pairs that need to be compared
	 * 
	 * @param views
	 * @param groups
	 * @return
	 */
	public abstract void definePairs();

	/**
	 * Reorder the pairs so that the "smaller" view comes first
	 */
	public void reorderPairs() { reorderPairs( pairs ); }

	/**
	 * Sorts each subset by comparing the first view of each pair, and then all subsets according to their first pair
	 *
	 * @param sets
	 */
	public void sortSets()
	{
		sortSets( subsets, new Comparator< Pair< V, V > >()
		{
			@Override
			public int compare( final Pair< V, V > o1, final Pair< V, V > o2 ) { return o1.getA().compareTo( o2.getA() ); }
		} );
	}

	/**
	 * Given a list of pairs of views that need to be compared, find subsets that are not overlapping
	 */
	public void detectSubsets() { this.subsets = detectSubsets( views, pairs, groups ); }

	/**
	 * Get a list of fixed views necessary for the specific strategy to work
	 */
	public abstract List< V > getDefaultFixedViews();

	/**
	 * Fix an additional list of views (removes them from pairs and subsets)
	 * 
	 * @param fixedViews
	 */
	public void fixViews( final List< V > fixedViews )
	{
		/*
			protected List< V > views;
			protected Collection< Collection< V > > groups;
			protected List< Pair< V, V > > pairs;
			protected ArrayList< ArrayList< Pair< V, V > > > subsets;
		 */

		final HashSet< V > viewsSet = new HashSet<>();
		viewsSet.addAll( views );

		for ( final V fixedView : fixedViews )
		{
			if ( viewsSet.contains( fixedView  ) )
			{
				fixedViews.add( fixedView );

				// remove all pairs where a fixed view is part of it
				for ( int i = pairs.size() - 1; i >= 0; --i )
				{
					final Pair< V, V > pair = pairs.get( i );

					if ( pair.getA().equals( fixedView ) || pair.getB().equals( fixedView ) )
						pairs.remove( i );
				}

				// remove all pairs where a fixed view is part of a subset
			}
		}
	}

	/**
	 * Reorder the pairs so that the "smaller" view comes first
	 */
	public static < V extends Comparable< V > > void reorderPairs( final List< Pair< V, V > > pairs )
	{
		for ( int i = 0; i < pairs.size(); ++i )
		{
			final Pair< V, V > pair = pairs.get( i );

			final V v1 = pair.getA();
			final V v2 = pair.getB();

			if ( v1.compareTo( v2 ) <= 0 )
				pairs.set( i, pair );
			else
				pairs.set( i, new ValuePair< V, V >( v2, v1 ) );
		}
	}

	/**
	 * Given a list of pairs of views that need to be compared, find subsets that are not overlapping
	 */
	public static < V > ArrayList< ArrayList< Pair< V, V > > > detectSubsets(
			final List< V > views,
			final List< Pair< V, V > > pairs,
			final Set< Set< V > > groups )
	{
		final ArrayList< ArrayList< Pair< V, V > > > pairSets = new ArrayList<>();
		final ArrayList< HashSet< V > > vSets = new ArrayList<>();

		// group all views by which will be compared
		for ( final Pair< V, V > pair : pairs )
		{
			final V v1 = pair.getA();
			final V v2 = pair.getB();

			int i1 = -1;
			int i2 = -1;

			for ( int i = 0; i < vSets.size(); ++i )
			{
				if ( vSets.get( i ).contains( v1 ) )
					i1 = i;

				if ( vSets.get( i ).contains( v2 ) )
					i2 = i;
			}

			// both are not contained in any set
			if ( i1 == -1 && i2 == -1 )
			{
				final ArrayList< Pair< V, V > > pairSet = new ArrayList<>();
				final HashSet< V > vSet = new HashSet<>();

				pairSet.add( pair );
				vSet.add( v1 );
				vSet.add( v2 );

				vSets.add( vSet );
				pairSets.add( pairSet );
			}
			else if ( i1 == -1 && i2 >= 0 ) // the first view is not present anywhere, but the second is
			{
				vSets.get( i2 ).add( v1 );
				pairSets.get( i2 ).add( pair );
			}
			else if ( i1 >= 0 && i2 == -1 ) // the second view is not present anywhere, but the first is
			{
				vSets.get( i1 ).add( v2 );
				pairSets.get( i1 ).add( pair );
			}
			else if ( i1 == i2 ) // both are already present in the same set
			{
				vSets.get( i1 ).add( v1 );
				vSets.get( i1 ).add( v2 );
				pairSets.get( i1 ).add( pair );
			}
			else // both are present in different sets, the sets need to be merged
			{
				mergeSets( vSets, pairSets, i1, i2 );
			}
		}

		// find individual views that are not part of a subset
		for ( final V v : views )
		{
			boolean isPresent = false;

			for ( final HashSet< V > groupedViews : vSets )
				if ( groupedViews.contains( v ) )
					isPresent = true;

			// add a new subset that only contains a single view, no pairs
			if ( !isPresent )
			{
				final ArrayList< Pair< V, V > > pairSet = new ArrayList<>();
				final HashSet< V > vSet = new HashSet<>();

				vSet.add( v );

				vSets.add( vSet );
				pairSets.add( pairSet );
			}
		}

		// now check if some of the sets are linked by grouping
		for ( final Set< V > group : groups )
			mergeSets( vSets, pairSets, containedInSets( group, vSets ) );

		return pairSets;
	}

	public static < V > HashSet< Integer > containedInSets( final Set< V > group, final List< ? extends Set< V > > vSets )
	{
		final HashSet< Integer > contained = new HashSet<>();

		// for each view of the group
		for ( final V view : group )
		{
			// is it present in more than one set?
			for ( int j = 0; j < vSets.size(); ++j )
			{
				if ( vSets.get( j ).contains( view ) )
				{
					contained.add( j );
				}
			}
		}

		return contained;
	}

	public static < V > void mergeSets(
			final ArrayList< HashSet< V > > vSets,
			final ArrayList< ArrayList< Pair< V, V > > > pairSets,
			final int i1, final int i2 )
	{
		final ArrayList< Integer > mergeIndicies = new ArrayList<>();
		mergeIndicies.add( i1 );
		mergeIndicies.add( i2 );
		mergeSets( vSets, pairSets, mergeIndicies );
	}

	public static < V > void mergeSets(
			final ArrayList< HashSet< V > > vSets,
			final ArrayList< ArrayList< Pair< V, V > > > pairSets,
			final Collection< Integer > mergeIndicies )
	{
		if ( mergeIndicies.size() <= 1 )
			return;

		final ArrayList< Integer > list = new ArrayList<>();
		list.addAll( mergeIndicies );
		Collections.sort( list ); // sort indicies from small to large

		final ArrayList< Pair< V, V > > pairSet = new ArrayList<>();
		final HashSet< V > vSet = new HashSet<>();

		for ( int i = 0; i < list.size(); ++i )
		{
			pairSet.addAll( pairSets.get( list.get( i ) ) );
			vSet.addAll( vSets.get( list.get( i ) ) );
		}

		// remove indicies from large down to small
		for ( int i = list.size() - 1; i >= 0; --i )
		{
			pairSets.remove( list.get( i ) );
			vSets.remove( list.get( i ) );
		}

		pairSets.add( pairSet );
		vSets.add( vSet );
	}

	public static < V > boolean oneSetContainsBoth(
			final V viewIdA,
			final V viewIdB,
			final Collection< ? extends Collection< V >> sets )
	{
		if ( sets == null )
			return false;

		for ( final Collection< V > set : sets )
			if ( set.contains( viewIdA ) && set.contains( viewIdB ) )
				return true;

		return false;
	}

	/**
	 * Sorts each list using a given comparator, and then the list according to their first element
	 *
	 * @param sets
	 */
	public static < V > void sortSets( final ArrayList< ArrayList< Pair< V, V > > > sets, final Comparator< Pair< V, V > > comp )
	{
		for ( final ArrayList< Pair< V, V > > list : sets )
			Collections.sort( list, comp );

		final Comparator< ArrayList< Pair< V, V > > > listComparator = new Comparator< ArrayList<Pair<V,V>> >()
		{
			@Override
			public int compare( final ArrayList< Pair< V, V > > o1, final ArrayList< Pair< V, V > > o2 )
			{
				return comp.compare( o1.get( 0 ), o2.get( 0 ) );
			}
		};

		Collections.sort( sets, listComparator );
	}

	public static void main( String[] args )
	{
		ArrayList< Integer > list = new ArrayList<>();
		list.add( 2 );
		list.add( 10 );
		list.add( 5 );

		Collections.sort( list );

		for ( int i = 0; i < list.size(); ++i )
			System.out.println( list.get( i ) );
	}
}
