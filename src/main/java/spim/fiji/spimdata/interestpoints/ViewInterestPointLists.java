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

import java.util.HashMap;

import mpicbg.spim.data.sequence.ViewId;

/**
 * Maps from a String label to a list of interest points for a specific viewid
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ViewInterestPointLists extends ViewId
{
	protected final HashMap< String, InterestPointList > lookup;
	
	public ViewInterestPointLists( final int timepointId, final int setupId )
	{
		super( timepointId, setupId );
		
		this.lookup = new HashMap< String, InterestPointList >();
	}
	
	public boolean contains( final String label ) { return lookup.containsKey( label ); }
	public HashMap< String, InterestPointList > getHashMap() { return lookup; }
	public InterestPointList getInterestPointList( final String label ) { return lookup.get( label ); }
	public void addInterestPointList( final String label, final InterestPointList pointList ) { lookup.put( label, pointList ); }
}
