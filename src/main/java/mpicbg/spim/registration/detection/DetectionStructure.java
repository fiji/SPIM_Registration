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

public class DetectionStructure< T extends DetectionView< ?, T > >
{
	final ArrayList< T > detections = new ArrayList< T >();
		
	public void addDetection( final T detection ) { detections.add( detection ); }
	public ArrayList<T> getDetectionList() { return detections; }
	public T getDetection( final long detectionID ) { return detections.get( (int)detectionID ); }
	
	public T getDetection( final float x, final float y, final float z )
	{
		for ( final T detection : getDetectionList() )
		{
			double[] location = detection.getL();
			
			if ( x == location[ 0 ] && y == location[ 1 ] && z == location[ 2 ] )
				return detection;
		}
		return null;
	}

	public void clearAllCorrespondenceCandidates()
	{
		for ( final T detection : getDetectionList() )
			detection.getDescriptorCorrespondence().clear();
	}
	
	public void clearAllRANSACCorrespondences()
	{
		for ( final T detection : getDetectionList() )
			detection.getRANSACCorrespondence().clear();
	}

	public void clearAllICPCorrespondences()
	{
		for ( final T detection : getDetectionList() )
			detection.getICPCorrespondence().clear();
	}
	
}
