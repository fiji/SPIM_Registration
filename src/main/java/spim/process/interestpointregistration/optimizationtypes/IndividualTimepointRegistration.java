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

/**
 * A registration type where each timepoint is registered individually
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IndividualTimepointRegistration extends GlobalOptimizationType
{
	public IndividualTimepointRegistration(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcess > channelsToProcess )
	{
		super( spimData, viewIdsToProcess, channelsToProcess, false );
	}

	@Override
	public List< GlobalOptimizationSubset > assembleAllViewPairs()
	{
		final ArrayList< GlobalOptimizationSubset > list = new ArrayList< GlobalOptimizationSubset >();
		
		for ( final TimePoint timepoint : SpimData2.getAllTimePointsSorted( spimData, viewIdsToProcess ) )
		{
			final HashMap< ViewId, MatchPointList > pointLists = this.getInterestPoints( timepoint );
			
			final ArrayList< ViewId > views = new ArrayList< ViewId >();
			views.addAll( pointLists.keySet() );
			Collections.sort( views );
			
			final ArrayList< PairwiseMatch > viewPairs = new ArrayList< PairwiseMatch >();
			
			// the views of the timepoint that is processed
			// add this only if we do not consider timepoints to be units
			// Note: if considerTimePointsAsUnits == true, there are no pairs
			if ( !considerTimePointsAsUnit() )
			{
				for ( int a = 0; a < views.size() - 1; ++a )
					for ( int b = a + 1; b < views.size(); ++b )
					{
						final ViewId viewIdA = views.get( a );
						final ViewId viewIdB = views.get( b );
						
						// only compare those to views if not both are fixed
						if ( !isFixedTile( viewIdA ) && !isFixedTile( viewIdB ) )
						{
							final MatchPointList listA = pointLists.get( viewIdA );
							final MatchPointList listB = pointLists.get( viewIdB );
	
							if ( !isValid( viewIdA, listA ) )
								continue;

							if ( !isValid( viewIdB, listB ) )
								continue;

							viewPairs.add( new PairwiseMatch( viewIdA, viewIdB, listA, listB ) );
						}
					}
			}
			list.add( new GlobalOptimizationSubset( viewPairs, "individual timepoint registration: " + timepoint.getName() + "(id=" + timepoint.getId() + ")" ) );
		}
		
		return list;
	}
}
