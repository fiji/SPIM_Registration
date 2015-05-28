package spim.process.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcess > channelsToProcess,
			final TimePoint referenceTimepoint,
			final boolean considerTimePointsAsUnit )
	{
		super( spimData, viewIdsToProcess, channelsToProcess, considerTimePointsAsUnit );

		this.setFixedTiles( assembleFixedTiles( spimData, viewIdsToProcess, channelsToProcess, referenceTimepoint ) );
		this.referenceTimepoint = referenceTimepoint;
	}
	
	/**
	 * All tiles of the reference timepoint are fixed, nothing else
	 * 
	 * @param spimData
	 * @param viewIdsToProcess
	 * @param channelsToProcess
	 * @param referenceTimepoint
	 * @return
	 */
	protected static HashSet< ViewId > assembleFixedTiles(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcess > channelsToProcess,
			final TimePoint referenceTimepoint )
	{
		final HashSet< ViewId > fixedTiles = new HashSet< ViewId >();
		
		for ( final ViewDescription vd : SpimData2.getAllViewIdsForTimePointSorted( spimData, viewIdsToProcess, referenceTimepoint ) )
		{
			if ( !vd.isPresent() )
				continue;

			for ( final ChannelProcess cp : channelsToProcess )
				if ( cp.getChannel().getId() == vd.getViewSetup().getChannel().getId() )
					fixedTiles.add( vd );
		}

		return fixedTiles;
	}

	public TimePoint getReferenceTimepoint() { return referenceTimepoint; }

	@Override
	public List< GlobalOptimizationSubset > assembleAllViewPairs()
	{
		final ArrayList< GlobalOptimizationSubset > list = new ArrayList< GlobalOptimizationSubset >();

		final HashMap< ViewId, MatchPointList > pointListsReferenceTimepoint = this.getInterestPoints( referenceTimepoint );

		for ( final TimePoint timepoint : SpimData2.getAllTimePointsSorted( spimData, viewIdsToProcess ) )
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

				if ( !isValid( viewIdA, listA ) )
					continue;

				for ( final ViewId viewIdB : fixedTiles )
				{
					final MatchPointList listB = pointListsReferenceTimepoint.get( viewIdB );

					if ( !isValid( viewIdB, listB ) )
						continue;

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

						if ( !isValid( viewIdA, listA ) )
							continue;

						if ( !isValid( viewIdB, listB ) )
							continue;

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
