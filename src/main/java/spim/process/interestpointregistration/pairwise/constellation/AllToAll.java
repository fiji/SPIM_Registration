package spim.process.interestpointregistration.pairwise.constellation;

import java.util.Collection;
import java.util.List;

import spim.process.interestpointregistration.pairwise.constellation.range.AllInRange;

public class AllToAll< V extends Comparable< V > > extends AllToAllRange< V >
{
	public AllToAll(
			final List< V > views,
			final Collection< Collection< V > > groups )
	{
		super( views, groups, new AllInRange<>() );
	}
}
