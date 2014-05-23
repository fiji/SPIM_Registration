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

public class AllToAllRegistrationWithRange extends GlobalOptimizationType
{
	final int range;
	
	public AllToAllRegistrationWithRange( final int range, final boolean remove, final boolean add, final boolean save, final boolean considerTimePointsAsUnit, final boolean fixFirstTile, final AbstractModel<?> mapBackModel )
	{ 
		super( remove, add, save, considerTimePointsAsUnit, fixFirstTile, mapBackModel );
		
		this.range = range;
	}

	public List< GlobalOptimizationSubset > getAllViewPairs(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess,
			final int inputTransform,
			final double minResolution )
	{
		final HashMap< ViewId, ChannelInterestPointList > allPointLists = new HashMap< ViewId, ChannelInterestPointList >();
		
		// collect all point lists from all timepoints
		for ( final TimePoint timepoint : timepointsToProcess )
		{
			final HashMap< ViewId, ChannelInterestPointList > pointLists = this.getInterestPoints(
					spimData,
					anglesToProcess,
					channelsToProcess,
					illumsToProcess,
					timepoint,
					inputTransform,
					minResolution );
			
			allPointLists.putAll( pointLists );
		}
		
		// all viewids of all timepoints
		final ArrayList< ViewId > views = new ArrayList< ViewId >();
		views.addAll( allPointLists.keySet() );
		Collections.sort( views );

		// all pairs that need to be compared
		final ArrayList< ChannelInterestPointListPair > viewPairs = new ArrayList< ChannelInterestPointListPair >();		

		for ( int a = 0; a < views.size() - 1; ++a )
			for ( int b = a + 1; b < views.size(); ++b )
			{
				final ViewId viewIdA = views.get( a );
				final ViewId viewIdB = views.get( b );
				
				if ( Math.abs( viewIdA.getTimePointId() - viewIdB.getTimePointId() ) <= range )
				{
					final ChannelInterestPointList listA = allPointLists.get( viewIdA );
					final ChannelInterestPointList listB = allPointLists.get( viewIdB );
					
					// in case we consider timepoints as units and the pair has the same timepoint, do not add;
					// i.e. add the pair always if the above statement is false
					if ( !( considerTimePointsAsUnit && ( viewIdA.getTimePointId() == viewIdB.getTimePointId() ) ) )
						viewPairs.add( new ChannelInterestPointListPair( viewIdA, viewIdB, listA, listB ) );
				}
			}

		final ArrayList< GlobalOptimizationSubset > list = new ArrayList< GlobalOptimizationSubset >();
		list.add( new GlobalOptimizationSubset( viewPairs, "all-to-all matching with range " + range + 
				" over all timepoints" ) );
		
		return list;
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
	public ViewId getReferenceTile( final GlobalOptimizationSubset set ) { return set.getViews().get( 0 ); }
}
