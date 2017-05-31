package spim.fiji.plugin.interestpointregistration.parameters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
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
	public boolean groupTimePoints, showStatistics;

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

	public HashSet< Group< ViewId > > getGroups( final SpimData2 data, final List< ViewId > views, final boolean groupTiles )
	{
		final HashSet< Group< ViewId > > groups = new HashSet<>();

		if ( groupTimePoints )
		{
			final ArrayList< Integer > timepoints = SpimData2.getAllTimePointsSortedUnchecked( views );

			//final HashSet< Class< ? extends Entity > > groupingFactor = new HashSet<>();
			//groupingFactor.add( TimePoint.class );
			//Group.splitBy( vds, groupingFactor )

			for ( final int tp : timepoints )
			{
				
				final Group< ViewId > group = new Group<>();

				for ( final ViewId viewId : views )
					if ( viewId.getTimePointId() == tp )
						group.getViews().add( viewId );

				groups.add( group );
			}

			IOFunctions.println( "Identified: " + groups.size() + " groups when grouping by TimePoint." );
			int i = 0;
			for ( final Group< ViewId > group : groups )
				IOFunctions.println( "Timepoint-Group " + (i++) + ":" + group );
		}

		// combine vs split
		if ( groupTiles )
		{
			final ArrayList< ViewDescription > vds = new ArrayList<>();

			for ( final ViewId viewId : views )
				vds.add( data.getSequenceDescription().getViewDescription( viewId ) );
	
			final HashSet< Class< ? extends Entity > > groupingFactor = new HashSet<>();
			groupingFactor.add( Tile.class );
			final List< Group< ViewDescription > > groupsTmp = Group.combineBy( vds, groupingFactor );

			IOFunctions.println( "Identified: " + groupsTmp.size() + " groups when grouping by Tiles." );
			int i = 0;
			for ( final Group< ViewDescription > group : groupsTmp )
			{
				IOFunctions.println( "Tile-Group " + (i++) + ":" + group );
				groups.add( (Group< ViewId >)(Object)group );
			}
		}

		return groups;
	}
}