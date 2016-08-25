package spim.process.interestpointregistration.pairwise.range;

import mpicbg.spim.data.sequence.ViewId;

public class TimepointRange implements RangeComparator< ViewId >
{
	final int maxDistance;

	public TimepointRange( final int maxDistance ) { this.maxDistance = maxDistance; }

	@Override
	public boolean inRange( final ViewId view1, final ViewId view2 )
	{
		if ( Math.abs( view2.getTimePointId() - view1.getTimePointId() ) <= maxDistance )
			return true;
		else
			return false;
	}
}
