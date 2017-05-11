package spim.fiji.plugin.interestpointregistration.parameters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.interestpointregistration.parameters.BasicRegistrationParameters.RegistrationType;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.pairwise.constellation.AllToAll;
import spim.process.interestpointregistration.pairwise.constellation.AllToAllRange;
import spim.process.interestpointregistration.pairwise.constellation.IndividualTimepoints;
import spim.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import spim.process.interestpointregistration.pairwise.constellation.ReferenceTimepoint;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.interestpointregistration.pairwise.constellation.range.TimepointRange;

public class AdvancedRegistrationParameters
{
	public int range, referenceTimePoint, fixViewsIndex, mapBackIndex;
	public boolean considerTimepointsAsUnit, showStatistics;

	public PairwiseSetup< ViewId > pairwiseSetupInstance(
			final RegistrationType registrationType,
			final List< ViewId > views,
			final Set< Group< ViewId > > groups )
	{
		if ( registrationType == RegistrationType.TIMEPOINTS_INDIVIDUALLY )
			return new IndividualTimepoints( views, groups );
		else if ( registrationType == RegistrationType.ALL_TO_ALL )
			return new AllToAll<>( views, groups );
		else if ( registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
			return new AllToAllRange< ViewId, TimepointRange< ViewId > >( views, groups, new TimepointRange<>( range ) );
		else
			return new ReferenceTimepoint( views, groups, referenceTimePoint );
	}

	public HashSet< Group< ViewId > > getGroups( final List< ViewId > views )
	{
		final HashSet< Group< ViewId > > groups = new HashSet<>();

		if ( considerTimepointsAsUnit )
		{
			final ArrayList< Integer > timepoints = SpimData2.getAllTimePointsSortedUnchecked( views );

			for ( final int tp : timepoints )
			{
				final Group< ViewId > group = new Group<>();

				for ( final ViewId viewId : views )
					if ( viewId.getTimePointId() == tp )
						group.getViews().add( viewId );

				groups.add( group );
			}
		}

		return groups;
	}
}