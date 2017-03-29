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
package mpicbg.spim.registration.segmentation;

import java.util.ArrayList;

import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.detection.DetectionIdentification;

/**
 * The BeadIdentification object stores the link (via ID) to an Bead-object and not an actual instance, but a link to the ViewDataBeads object where it belongs to.
 * This is necessary for storing/loading Bead-relationships to/from a text file. The Bead-objects of every {@link ViewDataBeads} have an {@link ArrayList} of BeadIdentification
 * objects telling which other Beads are correspondence candidates or true correspondences.
 *  
 * @author Stephan Preibisch
 *
 */
public class NucleusIdentification extends DetectionIdentification<NucleusIdentification, Nucleus>
{
	public NucleusIdentification( final Nucleus nucleus )
	{
		super( nucleus );
	}

	public NucleusIdentification( final int detectionID, final ViewDataBeads view )
	{
		super( detectionID, view );
	}
	
	public long getNucleusID() { return detectionID; }
	
	/**
	 * Prints the nucleus properties
	 */
	public String toString() { return "NucleusIdentification of " + getDetection().toString(); }	
	
	public Nucleus getNucleus() { return getDetection(); }
	
	/**
	 * Returns the actual {@link Nucleus} object it links to
	 * @return the {@link Nucleus} object
	 */
	public Nucleus getDetection()
	{
		return getDetection( false );
	}
	
	public Nucleus getDetection( final boolean silent )
	{		
		Nucleus nucleus = null;
		
		// this is just a good guess that might speed up a lot
		if ( detectionID < view.getNucleiStructure().getDetectionList().size() )
			nucleus = view.getNucleiStructure().getDetectionList().get( (int) detectionID );

		// check if it is the nucleus with the right ID
		if ( nucleus == null || nucleus.getID() != detectionID )
		{
			nucleus = null;
			for ( final Nucleus n : view.getNucleiStructure().getDetectionList() )
				if ( n.getID() == detectionID )
					nucleus = n;

			if ( nucleus == null && !silent )
			{
				IOFunctions.printErr( "NucleusIdentification.getNucleus(): Cannot find a nucleus for nucleusID=" + detectionID + " in view=" + view.getID() );
				return null;
			}
		}
		
		return nucleus;
	}
}
