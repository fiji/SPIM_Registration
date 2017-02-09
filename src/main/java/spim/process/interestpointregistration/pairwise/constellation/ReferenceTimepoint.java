package spim.process.interestpointregistration.pairwise.constellation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.process.interestpointregistration.pairwise.constellation.range.ReferenceTimepointRange;

public class ReferenceTimepoint extends AllToAllRange< ViewId, ReferenceTimepointRange< ViewId > >
{
	public ReferenceTimepoint(
			final List< ViewId > views,
			final Set< Set< ViewId > > groups,
			final int referenceTimepointId )
	{
		super( views, groups, new ReferenceTimepointRange<>( referenceTimepointId ) );
	}

	public ReferenceTimepoint(
			final List< ViewId > views,
			final Set< Set< ViewId > > groups,
			final TimePoint referenceTimepoint )
	{
		this( views, groups, referenceTimepoint.getId() );
	}

	/**
	 * Given a list of pairs of views that need to be compared, find subsets that are not overlapping
	 */
	@Override
	public void detectSubsets()
	{
		final ArrayList< ArrayList< Pair< ViewId, ViewId > > > pairSets = new ArrayList<>();
		final ArrayList< HashSet< ViewId > > vSets = new ArrayList<>();

		sdfsdf
		
		this.subsets = pairSets;
	}

	@Override
	public ArrayList< ViewId > getDefaultFixedViews()
	{
		final ArrayList< ViewId > fixed = new ArrayList<>();

		for ( final ViewId viewId : views )
			if ( viewId.getTimePointId() == rangeComparator.getReferenceTimepointId() )
				fixed.add( viewId );

		return fixed;
	}
}
