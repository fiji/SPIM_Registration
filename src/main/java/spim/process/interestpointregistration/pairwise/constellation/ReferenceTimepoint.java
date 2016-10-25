package spim.process.interestpointregistration.pairwise.constellation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.range.ReferenceTimepointRange;

public class ReferenceTimepoint< V extends ViewId > extends AllToAllRange< V >
{
	public ReferenceTimepoint(
			final List< V > views,
			final Collection< Collection< V > > groups,
			final int referenceTimepoint )
	{
		super( views, groups, new ReferenceTimepointRange<>( referenceTimepoint ) );
	}

	public ReferenceTimepoint(
			final List< V > views,
			final Collection< Collection< V > > groups,
			final TimePoint referenceTimepoint )
	{
		this( views, groups, referenceTimepoint.getId() );
	}

	@Override
	public List< V > getDefaultFixedViews()
	{
		return new ArrayList<>();
	}
}
