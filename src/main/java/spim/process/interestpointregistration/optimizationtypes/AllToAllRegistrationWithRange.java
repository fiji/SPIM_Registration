/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2023 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package spim.process.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.MatchPointList;
import spim.process.interestpointregistration.PairwiseMatch;

public class AllToAllRegistrationWithRange extends GlobalOptimizationType
{
	final int range;
	
	public AllToAllRegistrationWithRange(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcess > channelsToProcess,
			final int range,
			final boolean considerTimePointsAsUnit )
	{
		super( spimData, viewIdsToProcess, channelsToProcess, considerTimePointsAsUnit );

		this.range = range;
	}

	@Override
	public List< GlobalOptimizationSubset > assembleAllViewPairs()
	{
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
				
				// only compare those to views if not both are fixed and timepoints are within range
				if (
					!isFixedTile( viewIdA ) && !isFixedTile( viewIdB ) &&
					Math.abs( viewIdA.getTimePointId() - viewIdB.getTimePointId() ) <= range )
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
		list.add( new GlobalOptimizationSubset( viewPairs, "all-to-all matching with range " + range + 
				" over all timepoints" ) );
		
		return list;
	}
}
