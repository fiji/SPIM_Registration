package spim.process.interestpointregistration.pairwise.constellation;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import spim.process.interestpointregistration.pairwise.constellation.range.TimepointRange;

public class IndividualTimepoints extends AllToAllRange< ViewId >
{
	public IndividualTimepoints(
			final List< ViewId > views,
			final Set< Set< ViewId > > groups )
	{
		super( views, groups, new TimepointRange< ViewId >( 0 ) );
	}

	public static < V extends ViewId > List< Pair< V, V > > allPairs(
			final List< ? extends V > views,
			final Collection< ? extends Collection< V > > groups )
	{
		return AllToAllRange.allPairs( views, groups, new TimepointRange< V >( 0 ) );
	}
}
