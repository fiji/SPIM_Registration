package spim.process.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.MatchPointList;
import spim.process.interestpointregistration.PairwiseMatch;

public class ReferenceTimepointRegistration extends GlobalOptimizationType
{
	final TimePoint referenceTimepoint;

	public ReferenceTimepointRegistration(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess,
			final TimePoint referenceTimepoint,
			final boolean save,
			final boolean considerTimePointsAsUnit )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess, save, considerTimePointsAsUnit );

		this.setFixedTiles( assembleFixedTiles( spimData, anglesToProcess, channelsToProcess, illumsToProcess, referenceTimepoint ) );
		this.referenceTimepoint = referenceTimepoint;
	}
	
	/**
	 * All tiles of the reference timepoint are fixed, nothing else
	 * 
	 * @param spimData
	 * @param anglesToProcess
	 * @param channelsToProcess
	 * @param illumsToProcess
	 * @param referenceTimepoint
	 * @return
	 */
	protected static HashSet< ViewId > assembleFixedTiles(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final TimePoint referenceTimepoint )
	{
		final HashSet< ViewId > fixedTiles = new HashSet< ViewId >();
		
		for ( final Angle a : anglesToProcess )
			for ( final Illumination i : illumsToProcess )
				for ( final ChannelProcess c : channelsToProcess )
				{
					// bureaucracy
					final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), referenceTimepoint, c.getChannel(), a, i );
					
					// this happens only if a viewsetup is not present in any timepoint
					// (e.g. after appending fusion to a dataset)
					if ( viewId == null )
						continue;

					final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
							viewId.getTimePointId(), viewId.getViewSetupId() );
	
					if ( !viewDescription.isPresent() )
						continue;
					
					fixedTiles.add( viewId );
				}

		return fixedTiles;
	}

	public TimePoint getReferenceTimepoint() { return referenceTimepoint; }

	@Override
	public List< GlobalOptimizationSubset > assembleAllViewPairs()
	{
		final ArrayList< GlobalOptimizationSubset > list = new ArrayList< GlobalOptimizationSubset >();

		final HashMap< ViewId, MatchPointList > pointListsReferenceTimepoint = this.getInterestPoints( referenceTimepoint );

		for ( final TimePoint timepoint : timepointsToProcess )
		{
			if ( timepoint == referenceTimepoint )
				continue;
			
			final HashMap< ViewId, MatchPointList > pointListsTimepoint = this.getInterestPoints( timepoint );
			
			final ArrayList< ViewId > views = new ArrayList< ViewId >();
			views.addAll( pointListsTimepoint.keySet() );
			Collections.sort( views );
			
			final ArrayList< PairwiseMatch > viewPairs = new ArrayList< PairwiseMatch >();
			
			// all correspondences between the reference timepoint and the current timepoint
			for ( int a = 0; a < views.size(); ++a )
			{
				final ViewId viewIdA = views.get( a );
				final MatchPointList listA = pointListsTimepoint.get( viewIdA );
				
				for ( final ViewId viewIdB : fixedTiles )
				{
					final MatchPointList listB = pointListsReferenceTimepoint.get( viewIdB );
					viewPairs.add( new PairwiseMatch( viewIdA, viewIdB, listA, listB ) );
				}
			}
			
			// the views of the timepoint that is processed
			// add this only if we do not consider timepoints to be units
			if ( !considerTimePointsAsUnit() )
			{
				for ( int a = 0; a < views.size() - 1; ++a )
					for ( int b = a + 1; b < views.size(); ++b )
					{
						final ViewId viewIdA = views.get( a );
						final ViewId viewIdB = views.get( b );
						
						final MatchPointList listA = pointListsTimepoint.get( viewIdA );
						final MatchPointList listB = pointListsTimepoint.get( viewIdB );
						
						viewPairs.add( new PairwiseMatch( viewIdA, viewIdB, listA, listB ) );
					}
			}
			
			list.add( new GlobalOptimizationSubset( viewPairs, "reference timepoint ( " + referenceTimepoint.getName() + ", id=" + referenceTimepoint.getId() + 
					") registration: " + timepoint.getName() + "(id=" + timepoint.getId() + ")" ) );
		}
		
		return list;
	}

	@Override
	public ViewId getMapBackReferenceTile( final GlobalOptimizationSubset set ) { return null; }
}
