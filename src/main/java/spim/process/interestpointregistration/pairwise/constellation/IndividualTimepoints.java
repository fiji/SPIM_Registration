package spim.process.interestpointregistration.pairwise.constellation;

import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.range.TimepointRange;

public class IndividualTimepoints< V extends ViewId > extends AllToAllRange< V >
{
	public IndividualTimepoints()
	{
		super( new TimepointRange< V >( 0 ) );
	}
}
