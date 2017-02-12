package spim.process.interestpointregistration.pairwise.constellation.grouping;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
}
