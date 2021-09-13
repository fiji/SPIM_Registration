/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2021 Fiji developers.
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
package spim.fiji.spimdata.interestpoints;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

/**
 * A class that organizes all interest point detections of all {@link ViewDescription}s (which extend {@link ViewId})
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ViewInterestPoints
{
	protected final Map< ViewId, ViewInterestPointLists > interestPointCollectionLookup;

	public ViewInterestPoints()
	{
		interestPointCollectionLookup = new HashMap< ViewId, ViewInterestPointLists >();
	}

	public ViewInterestPoints( final Map< ViewId, ViewInterestPointLists > interestPointCollectionLookup )
	{
		this.interestPointCollectionLookup = interestPointCollectionLookup;
	}

	public ViewInterestPoints( final Collection< ViewInterestPointLists > interestPointCollection )
	{
		this.interestPointCollectionLookup = new HashMap< ViewId, ViewInterestPointLists >();
		
		for ( final ViewInterestPointLists v : interestPointCollection )
			this.interestPointCollectionLookup.put( v, v );
	}
	
	public Map< ViewId, ViewInterestPointLists > getViewInterestPoints() { return interestPointCollectionLookup; }
	
	public ViewInterestPointLists getViewInterestPointLists( final int tp, final int setupId )
	{
		return getViewInterestPointLists( new ViewId( tp, setupId ) );
	}

	public ViewInterestPointLists getViewInterestPointLists( final ViewId viewId )
	{
		return interestPointCollectionLookup.get( viewId );
	}
	
	/**
	 * Assembles the {@link ViewInterestPoints} object consisting of a list of {@link ViewInterestPointLists} objects for all {@link ViewDescription}s that are present
	 *
	 * @param viewDescriptions
	 *
	 */
	public void createViewInterestPoints( final Map< ViewId, ViewDescription > viewDescriptions )
	{
		for ( final ViewDescription viewDescription : viewDescriptions.values() )
			if ( viewDescription.isPresent() )
				interestPointCollectionLookup.put( viewDescription, new ViewInterestPointLists( viewDescription.getTimePointId(), viewDescription.getViewSetupId() ) );
	}
}
