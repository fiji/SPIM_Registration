package spim.process.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import mpicbg.models.AbstractModel;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelInterestPointList;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.ChannelProcess;

/**
 * A registration type where each timepoint is registered individually
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IndividualTimepointRegistration extends GlobalOptimizationType
{
	public IndividualTimepointRegistration( final boolean remove, final boolean add, final boolean save, final boolean fixFirstTile, final AbstractModel<?> mapBackModel )
	{ 
		super( remove, add, save, false, fixFirstTile, mapBackModel );
	}

	@Override
	public boolean isFixedTile( final ViewId viewId, final GlobalOptimizationSubset set )
	{
		// fix first tile
		if ( fixFirstTile && viewId == set.getViews().get( 0 ) )
			return true;
		else
			return false;
	}

	@Override
	public List< GlobalOptimizationSubset > getAllViewPairs(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
	{
		final ArrayList< GlobalOptimizationSubset > list = new ArrayList< GlobalOptimizationSubset >();
		
		for ( final TimePoint timepoint : timepointsToProcess )
		{
			final HashMap< ViewId, ChannelInterestPointList > pointLists = this.getInterestPoints(
					spimData,
					anglesToProcess,
					channelsToProcess,
					illumsToProcess,
					timepoint );
			
			final ArrayList< ViewId > views = new ArrayList< ViewId >();
			views.addAll( pointLists.keySet() );
			Collections.sort( views );
			
			final ArrayList< ChannelInterestPointListPair > viewPairs = new ArrayList< ChannelInterestPointListPair >();
			
			// the views of the timepoint that is processed
			// add this only if we do not consider timepoints to be units
			// Note: if considerTimePointsAsUnits == true, there are no pairs
			if ( !considerTimePointsAsUnit )
			{
				for ( int a = 0; a < views.size() - 1; ++a )
					for ( int b = a + 1; b < views.size(); ++b )
					{
						final ViewId viewIdA = views.get( a );
						final ViewId viewIdB = views.get( b );
						
						final ChannelInterestPointList listA = pointLists.get( viewIdA );
						final ChannelInterestPointList listB = pointLists.get( viewIdB );
						
						viewPairs.add( new ChannelInterestPointListPair( viewIdA, viewIdB, listA, listB ) );
					}
			}
			list.add( new GlobalOptimizationSubset( viewPairs, "individual timepoint registration: " + timepoint.getName() + "(id=" + timepoint.getId() + ")" ) );
		}
		
		return list;
	}

	@Override
	public ViewId getReferenceTile( final GlobalOptimizationSubset set ) { return set.getViews().get( 0 ); }
}