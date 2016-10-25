package spim.process.interestpointregistration.pairwise.constellation;

import java.util.Collection;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.range.TimepointRange;

public class IndividualTimepoints extends AllToAllRange< ViewId >
{
	public IndividualTimepoints(
			final List< ViewId > views,
			final Collection< Collection< ViewId > > groups )
	{
		super( views, groups, new TimepointRange< ViewId >( 0 ) );
	}
}
