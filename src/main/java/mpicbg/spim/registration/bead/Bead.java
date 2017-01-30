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
package mpicbg.spim.registration.bead;

import spim.vecmath.Point3d;

import net.imglib2.util.Util;
import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.detection.DetectionView;

public class Bead extends DetectionView< BeadIdentification, Bead >
{
	// so the gauss fit knows if it relocalize it already
	public boolean relocalized = false;
	
	private static final long serialVersionUID = -2875282502611466531L;
	
	public Bead( final int id, final Point3d location, final ViewDataBeads myView )
	{
		super( id, new double[] { location.x, location.y, location.z}, myView );
	}

	public Bead( final int id, final double[] location, final ViewDataBeads myView )
	{
		super( id, location, myView );
	}
			
	@Override
	public String toString()
	{
		String desc = "Bead " + getID() + " l"+ Util.printCoordinates( getL() ) + "; w"+ Util.printCoordinates( getW() );
		
		if ( myView != null)
			return desc + " of view " + myView;
		else
			return desc + " - no view assigned";
	}
	
	public boolean equals( final Bead bead )
	{
		if ( this.getID() == bead.getID() && this.getViewID() == bead.getViewID() )
			return true;
		else
			return false;
	}

	public boolean equals( final BeadIdentification beadID )
	{
		if ( this.getID() == beadID.getDetectionID() && this.getViewID() == beadID.getViewID() )
			return true;
		else
			return false;
	}

	@Override
	public Bead[] createArray( final int n ){ return new Bead[ n ];	}

	@Override
	public BeadIdentification createIdentification() 
	{
		return new BeadIdentification( this );
	}	
}
