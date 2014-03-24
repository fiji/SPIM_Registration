package spim.fiji.plugin.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import spim.fiji.plugin.interestpointregistration.ChannelInterestPointList;
import spim.fiji.plugin.interestpointregistration.ChannelInterestPointListPair;
import spim.fiji.plugin.interestpointregistration.ChannelProcess;
import spim.fiji.spimdata.SpimData2;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;

/**
 * A registration type where each timepoint is registered individually
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IndividualTimepointRegistration extends GlobalOptimizationType
{
	public IndividualTimepointRegistration( final boolean save ) { super( save ); }

	@Override
	public boolean isFixedTile( final ViewId viewId, final GlobalOptimizationSubset set )
	{
		// fix first tile
		if ( viewId == set.getViews().get( 0 ) )
			return true;
		else
			return false;
	}

	@Override
	public List< GlobalOptimizationSubset > getAllViewPairs(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess,
			final int inputTransform,
			final double minResolution )
	{
		final ArrayList< GlobalOptimizationSubset > list = new ArrayList< GlobalOptimizationSubset >();
		
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
			
			final ArrayList< ViewId > views = new ArrayList< ViewId >();
			views.addAll( pointLists.keySet() );
			Collections.sort( views );
			
			final ArrayList< ChannelInterestPointListPair > viewPairs = new ArrayList< ChannelInterestPointListPair >();
			
			for ( int a = 0; a < views.size() - 1; ++a )
				for ( int b = a + 1; b < views.size(); ++b )
				{
					final ViewId viewIdA = views.get( a );
					final ViewId viewIdB = views.get( b );
					
					final ChannelInterestPointList listA = pointLists.get( viewIdA );
					final ChannelInterestPointList listB = pointLists.get( viewIdB );
					
					viewPairs.add( new ChannelInterestPointListPair( viewIdA, viewIdB, listA, listB ) );
				}
			
			list.add( new GlobalOptimizationSubset( viewPairs, "individual timepoint registration: " + timepoint.getName() + "(id=" + timepoint.getId() + ")" ) );
		}
		
		return list;
	}
}