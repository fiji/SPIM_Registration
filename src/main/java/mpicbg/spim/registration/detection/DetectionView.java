/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji developers.
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
package mpicbg.spim.registration.detection;

import java.util.ArrayList;
import java.util.Collection;

import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.registration.ViewDataBeads;

public abstract class DetectionView< S extends DetectionIdentification< S, T >, T extends DetectionView< S, T > > extends AbstractDetection< T > 
{
	private static final long serialVersionUID = 1L;
	
	final protected ViewDataBeads myView;
	final protected ArrayList<S> descriptorCorrespondence = new ArrayList<S>();
	final protected ArrayList<S> ransacCorrespondence = new ArrayList<S>();
	final protected ArrayList<S> icpCorrespondence = new ArrayList<S>();
	
	public DetectionView( final int id, final double[] location, final ViewDataBeads myView ) 
	{
		super( id, location );
		this.myView = myView;
	}
	
	public abstract S createIdentification();

	public ViewDataBeads getView() { return myView; }
	public int getViewID() { return myView.getID(); }	
	
	public ArrayList<S> getDescriptorCorrespondence() { return descriptorCorrespondence; }
	public ArrayList<S> getRANSACCorrespondence() { return ransacCorrespondence; }
	public ArrayList<S> getICPCorrespondence() { return icpCorrespondence; }
		
	public synchronized void addPointDescriptorCorrespondence( final T detection ) { addPointDescriptorCorrespondence( detection, 1 ); }
	public synchronized void addPointDescriptorCorrespondence( final T detection, final double weight )
	{		
		descriptorCorrespondence.add( detection.createIdentification() );
	}
	
	public synchronized void addICPCorrespondence( final T detection ) { addICPCorrespondence( detection, 1 ); }
	public synchronized void addICPCorrespondence( final T detection, final double weight )
	{		
		icpCorrespondence.add( detection.createIdentification() );
	}

	public synchronized void addRANSACCorrespondence( final T detection ) { addRANSACCorrespondence( detection, 1 ); }
	public synchronized void addRANSACCorrespondence( final T detection, final double weight )
	{
		if ( !containsDetectionID( descriptorCorrespondence, detection ) )
		{
			IOFunctions.println( "Error: Cannot set RANSAC correspondence for bead " + this +"; it has no correspondence from a point descriptor." );
			return;
		}

		int sameViewIndex = -1;
		
		for ( int i = 0; i < ransacCorrespondence.size(); i++ )
			if ( ransacCorrespondence.get( i ).getViewID() == detection.getViewID() )
				sameViewIndex = i;
		
		if ( sameViewIndex >= 0 )
		{
			IOFunctions.println( "Warning: RANSAC Correspondence for Detection " + this + ", currently " + ransacCorrespondence.get( sameViewIndex ) + " overwritten by " + detection );
			ransacCorrespondence.remove( sameViewIndex );
		}

		ransacCorrespondence.add( detection.createIdentification() );
	}
	
	public static < S extends DetectionIdentification<S,T>, T extends DetectionView<S,T> > boolean containsDetectionID( final Collection< S > list, final T detection )
	{
		boolean contains = false;
		
		for ( final S content : list )
			if ( content.getDetectionID() == detection.getID() && content.getViewID() == detection.getViewID() )
				contains = true;
		
		return contains;
	}

	public static < T extends DetectionView<?,T> > boolean containsDetection( final Collection< T > list, final T detection )
	{
		boolean contains = false;
		
		for ( final DetectionView<?,T> content : list )
			if ( content.getID() == detection.getID() && content.getViewID() == detection.getViewID() )
				contains = true;
		
		return contains;
	}

	public boolean equals( final DetectionView<?,?> otherDetection )
	{
		if ( this.getID() == otherDetection.getID() && this.getViewID() == otherDetection.getViewID() )
			return true;
		else
			return false;
	}	
}
