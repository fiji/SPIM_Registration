package spim.process.interestpointregistration.pairwise.constellation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public abstract class PairwiseSetup< V extends Comparable< V > >
{
	protected List< V > views;
	protected Collection< Collection< V > > groups;
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
	public PairwiseSetup( final List< V > views, final Collection< Collection< V > > groups )
	{
		this.views = views;
		this.groups = groups;
	}

	public PairwiseSetup( final List< V > views ) { this( views, null ); }

	public List< V > getViews() { return views; }
	public void setViews( final List< V > views ) { this.views = views; }

	public Collection< Collection< V > > getGroups() { return groups; }
	public void setGroups( final Collection< Collection< V > > groups ) { this.groups = groups; }

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
	public void reorderPairs()
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
		for ( fi nal V view : fixedViews )
		{
		
		}
	}

	/**
	 * Given a list of pairs of views that need to be compared, find subsets that are not overlapping
	 */
	public void detectSubsets()
	{
		final ArrayList< ArrayList< Pair< V, V > > > pairSets = new ArrayList<>();
		final ArrayList< HashSet< V > > vSets = new ArrayList<>();

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
				final ArrayList< Pair< V, V > > pairSet = new ArrayList<>();
				final HashSet< V > vSet = new HashSet<>();

				pairSet.addAll( pairSets.get( i1 ) );
				pairSet.addAll( pairSets.get( i2 ) );
				vSet.addAll( vSets.get( i1 ) );
				vSet.addAll( vSets.get( i2 ) );

				// change the indicies so the bigger one is last
				if ( i1 > i2 )
				{
					final int tmp = i2;
					i2 = i1;
					i1 = tmp;
				}

				pairSets.remove( i2 );
				pairSets.remove( i1 );
				vSets.remove( i2 );
				vSets.remove( i1 );

				pairSets.add( pairSet );
				vSets.add( vSet );
			}
		}

		this.subsets = pairSets;
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
}
