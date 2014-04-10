package spim.process.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelInterestPointList;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.ChannelProcess;

public class AllToAllRegistration extends GlobalOptimizationType
{
	public AllToAllRegistration( final boolean remove, final boolean add, final boolean save )
	{ 
		super( remove, add, save );
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
				
				final ChannelInterestPointList listA = allPointLists.get( viewIdA );
				final ChannelInterestPointList listB = allPointLists.get( viewIdB );
				
				viewPairs.add( new ChannelInterestPointListPair( viewIdA, viewIdB, listA, listB ) );
			}

		final ArrayList< GlobalOptimizationSubset > list = new ArrayList< GlobalOptimizationSubset >();
		list.add( new GlobalOptimizationSubset( viewPairs, "all-to-all matching over all timepoints" ) );
		
		return list;
	}

	@Override
	public boolean isFixedTile( final ViewId viewId, final GlobalOptimizationSubset set )
	{
		// fix first tile
		if ( viewId == set.getViews().get( 0 ) )
			return true;
		else
			return false;
	}
}
