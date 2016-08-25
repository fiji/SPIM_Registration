package spim.fiji.plugin.interestpointregistration.global;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.plugin.interestpointregistration.ChannelProcessGUI;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.pairwise.PairwiseStrategyTools;

public class AllToAllGUI extends GlobalOptimizationGUI
{
	public AllToAllGUI(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public int getNumSets() { return 1; }

	@Override
	public List< Pair< ViewId, ViewId > > defineViewPairs( final int set )
	{
		if ( set != 0 )
			throw new RuntimeException( "There is only one set of pairwise comparisions." );

		PairwiseStrategyTools.allToAll( views, fixed, groups );

		final HashMap< ViewId, MatchPointList > allPointLists = new HashMap< ViewId, MatchPointList >();
		
		// collect all point lists from all timepoints
		for ( final TimePoint timepoint : SpimData2.getAllTimePointsSorted( spimData, viewIdsToProcess ) )
		{
			final HashMap< ViewId, MatchPointList > pointLists = this.getInterestPoints( timepoint );
			
			allPointLists.putAll( pointLists );
		}
		
		// all viewids of all timepoints
		final ArrayList< ViewId > views = new ArrayList< ViewId >();
		views.addAll( allPointLists.keySet() );
		Collections.sort( views );

		// all pairs that need to be compared
		final ArrayList< PairwiseMatch > viewPairs = new ArrayList< PairwiseMatch >();		

		for ( int a = 0; a < views.size() - 1; ++a )
			for ( int b = a + 1; b < views.size(); ++b )
			{
				final ViewId viewIdA = views.get( a );
				final ViewId viewIdB = views.get( b );
				
				// only compare those to views if not both are fixed
				if ( !isFixedTile( viewIdA ) && !isFixedTile( viewIdB ) )
				{
					final MatchPointList listA = allPointLists.get( viewIdA );
					final MatchPointList listB = allPointLists.get( viewIdB );

					if ( !isValid( viewIdA, listA ) )
						continue;

					if ( !isValid( viewIdB, listB ) )
						continue;

					// in case we consider timepoints as units and the pair has the same timepoint, do not add;
					// i.e. add the pair always if the above statement is false
					if ( !( considerTimePointsAsUnit() && ( viewIdA.getTimePointId() == viewIdB.getTimePointId() ) ) )
						viewPairs.add( new PairwiseMatch( viewIdA, viewIdB, listA, listB ) );
				}
			}

		final ArrayList< GlobalOptimizationSubset > list = new ArrayList< GlobalOptimizationSubset >();
		list.add( new GlobalOptimizationSubset( viewPairs, "all-to-all matching over all timepoints" ) );
		
		return list;
	}

	public String getDescription() { return "all-to-all matching over all timepoints"; }
}
