package spim.process.interestpointregistration.pairwise.constellation.grouping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class Group< V > implements Iterable< V >
{
	private HashSet< V > views;

	public Group( final Collection< V > views )
	{
		this.views = new HashSet<>();
		this.views.addAll( views );
	}

	public Group( final V view )
	{
		this.views = new HashSet<>();
		this.views.add( view );
	}

	public Group()
	{
		this.views = new HashSet<>();
	}

	public Set< V > getViews() { return views; }
	public int size() { return views.size(); }
	public boolean contains( final V view ) { return views.contains( view ); }

	@Override
	public Iterator< V > iterator() { return views.iterator(); }

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ( ( views == null ) ? 0 : views.hashCode() );
		return result;
	}

	@Override
	public boolean equals( Object obj )
	{
		if ( this == obj )
			return true;

		if ( obj == null )
			return false;

		if ( getClass() != obj.getClass() )
			return false;

		final Group< ? > other = (Group< ? >) obj;

		if ( views == null )
		{
			if ( other.views != null )
				return false;
		}
		else if ( !views.equals( other.views ) )
		{
			return false;
		}

		return true;
	}

	@Override
	public String toString() { return gvids( this ); }

	public static < V extends ViewId > ArrayList< Group< V > > groupByChannel( final List< V > viewIds, final AbstractSequenceDescription< ?, ? extends BasicViewDescription< ? >, ? > sd )
	{
		final ArrayList< V > input = new ArrayList<>();
		final ArrayList< Group< V > > grouped = new ArrayList<>();

		input.addAll( viewIds );

		while ( input.size() > 0 )
		{
			final BasicViewDescription< ? > vd1 = sd.getViewDescriptions().get( input.get( 0 ) );
			final ArrayList< V > localGroup = new ArrayList<>();
			localGroup.add( input.get( 0 ) );
			input.remove( 0 );

			for ( int i = input.size() - 1; i >=0; --i )
			{
				boolean attributesSame = true;

				final BasicViewDescription< ? > vd2 = sd.getViewDescriptions().get( input.get( i ) );

				final int id1 = vd1.getViewSetup().getAttribute( Channel.class ).getId();
				final int id2 = vd2.getViewSetup().getAttribute( Channel.class ).getId();

				// same timepoint, different channel
				if ( vd1.getTimePointId() == vd2.getTimePointId() && id1 != id2 )
				{
					final Map< String, Entity > map1 = vd1.getViewSetup().getAttributes();
					final Map< String, Entity > map2 = vd2.getViewSetup().getAttributes();

					for ( final String key : map1.keySet() )
					{
						if ( key.toLowerCase().equals( "channel" ) )
							continue;

						if ( map1.containsKey( key ) && map2.containsKey( key ) )
						{
							if ( !map1.get( key ).equals( map2.get( key ) ) )
								attributesSame = false;
						}
						else
						{
							attributesSame = false;
						}
					}
				}
				else
				{
					attributesSame = false;
				}
				
				if ( attributesSame )
				{
					localGroup.add( input.get( i ) );
					input.remove( i );
				}
			}

			// sort by channel, so it is always the same order
			Collections.sort( localGroup, new Comparator< ViewId >()
			{
				@Override
				public int compare( final ViewId o1, final ViewId o2 )
				{
					final int id1 = sd.getViewDescriptions().get( o1 ).getViewSetup().getAttribute( Channel.class ).getId();
					final int id2 = sd.getViewDescriptions().get( o2 ).getViewSetup().getAttribute( Channel.class ).getId();

					return id1 - id2;
				}
			} );

			grouped.add( new Group< V >( localGroup ) );
		}

		return grouped;
	}

	public static < V > boolean containsBoth(
			final V viewIdA,
			final V viewIdB,
			final Collection< ? extends Group< V > > groupCollection )
	{
		if ( groupCollection == null )
			return false;

		for ( final Group< V > group : groupCollection )
			if ( group.contains( viewIdA ) && group.contains( viewIdB ) )
				return true;

		return false;
	}

	public static < V > Group< V > isContained(
			final V view,
			final Collection< ? extends Group< V > > groupCollection )
	{
		if ( groupCollection == null )
			return null;

		for ( final Group< V > group : groupCollection )
			if ( group.contains( view ) )
				return group;

		return null;
	}

	/**
	 * Test if there is any overlap between two groups (at least one view part of both)
	 *
	 * @param groupA
	 * @param groupB
	 * @return
	 */
	public static < V > boolean overlaps( final Group< V > groupA, final Group< V > groupB )
	{
		for ( final V viewA : groupA )
			if ( groupB.contains( viewA ) )
				return true;

		return false;
	}

	/**
	 * Identifies all groups that a certain view is a member of
	 * 
	 * @param view
	 * @param groups
	 * @return
	 */
	public static < V > ArrayList< Group< V > > memberOf( final V view, final Set< Group< V > > groups )
	{
		final ArrayList< Group< V > > memberOf = new ArrayList<>();

		for ( final Group< V > group : groups )
			if ( group.getViews().contains( view ) )
				memberOf.add( group );

		return memberOf;
	}

	/**
	 * Identify all pairs of overlapping groups
	 *
	 * @param groups
	 * @return
	 */
	public static < V > ArrayList< Pair< Group< V >, Group< V > > > overlappingGroups( final Collection< Group< V > > groups )
	{
		final ArrayList< Group< V > > g = new ArrayList<>();
		g.addAll( groups );

		final ArrayList< Pair< Group< V >, Group< V > > > overlappingGroups = new ArrayList<>();

		for ( int a = 0; a < g.size() - 1; ++a )
			for ( int b = a + 1; b < g.size(); ++b )
				if ( overlaps( g.get( a ), g.get( b ) ) )
					overlappingGroups.add( new ValuePair<>( g.get( a ), g.get( b ) ) );

		return overlappingGroups;
	}

	/**
	 * Merges all overlapping groups into a new List, the input remains unchanged
	 *
	 * @param groups
	 */
	public static < V > ArrayList< Group< V > > mergeAllOverlappingGroups( final Collection< Group< V > > groups )
	{
		final ArrayList< Group< V > > g = new ArrayList<>();
		g.addAll( groups );

		while ( true )
		{
			final Pair< Integer, Integer > pair = nextOverlappingGroup( g );

			if ( pair == null )
				break;

			final int indexA = pair.getA();
			final int indexB = pair.getB();

			final Group< V > groupA = g.get( indexA );
			final Group< V > groupB = g.get( indexB );
	
			g.remove( indexB ); // always bigger then indexA
			g.remove( indexA );

			g.add( merge( groupA, groupB ) );
		}

		return g;
	}

	/**
	 * Identify the indices of the next overlapping groups ordered by size, or return null if none are overlapping
	 *
	 * @param groups
	 * @return
	 */
	public static < V > Pair< Integer, Integer > nextOverlappingGroup( final List< Group< V > > groups )
	{
		for ( int a = 0; a < groups.size() - 1; ++a )
			for ( int b = a + 1; b < groups.size(); ++b )
				if ( overlaps( groups.get( a ), groups.get( b ) ) )
					return new ValuePair<>( a, b );

		return null;
	}

	/**
	 * Merges two Groups of views
	 *
	 * @param groupA
	 * @param groupB
	 * @return
	 */
	public static < V > Group< V > merge( final Group< V > groupA, final Group< V > groupB  )
	{
		final ArrayList< V > list = new ArrayList<>();

		list.addAll( groupA.getViews() );
		list.addAll( groupB.getViews() );

		return new Group<>( list );
	}

	/**
	 * Merges two Groups of views
	 *
	 * @param groupA
	 * @param groupB
	 * @return
	 */
	public static < V > Group< V > mergeOverlapping( final Group< V > groupA, final Group< V > groupB  )
	{
		final ArrayList< V > list = new ArrayList<>();

		list.addAll( groupA.getViews() );
		list.addAll( groupB.getViews() );

		return new Group<>( list );
	}

	public static < V > void removeEmptyGroups( final List< Group < V > > groups )
	{
		for ( int i = groups.size() - 1; i >= 0; --i )
			if ( groups.get( i ).size() == 0 )
				groups.remove( i );
	}

	public static String pvid( final ViewId viewId ) { return "tpId=" + viewId.getTimePointId() + " setupId=" + viewId.getViewSetupId(); }
	public static String pvids( final ViewId viewId ) { return "t(" + viewId.getTimePointId() + ")-s(" + viewId.getViewSetupId() + ")"; }
	public static String gvids( final Group< ? > group )
	{
		String groupS = "";

		for ( final Object a : group.getViews() )
		{
			if ( ViewId.class.isInstance( a ) )
				groupS += pvids( (ViewId)a ) + " ";
			else
				groupS += "? ";
		}

		return groupS.trim();
	}

	public static void main( String[] args )
	{
		final ViewId v0 = new ViewId( 0, 0 );
		final ViewId v1 = new ViewId( 0, 1 );
		final ViewId v2 = new ViewId( 0, 2 );
		final ViewId v3 = new ViewId( 1, 0 );
		final ViewId v4 = new ViewId( 1, 1 );
		final ViewId v5 = new ViewId( 1, 2 );

		final Group< ViewId > g0 = new Group<>();
		final Group< ViewId > g1 = new Group<>();
		final Group< ViewId > g2 = new Group<>();

		g0.getViews().add( v0 );
		g0.getViews().add( v2 );
		g0.getViews().add( v5 );

		g1.getViews().add( v1 );
		g1.getViews().add( v3 );
		//g1.getViews().add( v4 );

		g2.getViews().add( v2 );
		g2.getViews().add( v4 );

		final ArrayList< Group< ViewId > > groups = new ArrayList<>();
		groups.add( g0 );
		groups.add( g1 );
		groups.add( g2 );

		for ( final Group< ViewId > g : mergeAllOverlappingGroups( groups ) )
		{
			System.out.println( g );
		}
	}
}
