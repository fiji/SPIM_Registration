package spim.process.interestpointregistration.pairwise.constellation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.range.ReferenceTimepointRange;

public class ReferenceTimepoint extends AllToAllRange< ViewId >
{
	public ReferenceTimepoint(
			final List< ViewId > views,
			final Collection< Collection< ViewId > > groups,
			final int referenceTimepoint )
	{
		super( views, groups, new ReferenceTimepointRange<>( referenceTimepoint ) );
	}

	public ReferenceTimepoint(
			final List< ViewId > views,
			final Collection< Collection< ViewId > > groups,
			final TimePoint referenceTimepoint )
	{
		this( views, groups, referenceTimepoint.getId() );
	}

	@Override
	public List< ViewId > getDefaultFixedViews()
	{
		return new Arr ayList<>();
	}
}
