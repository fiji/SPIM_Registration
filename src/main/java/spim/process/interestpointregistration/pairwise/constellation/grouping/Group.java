package spim.process.interestpointregistration.pairwise.constellation.grouping;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import mpicbg.spim.data.sequence.ViewId;

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
}
