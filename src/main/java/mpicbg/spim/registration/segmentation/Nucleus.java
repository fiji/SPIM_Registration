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
package mpicbg.spim.registration.segmentation;

import net.imglib2.util.Util;
import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.detection.DetectionView;

public class Nucleus extends DetectionView<NucleusIdentification, Nucleus>
{
	private static final long serialVersionUID = 1L;

	protected double weight = 1;
	double diameter = 1;

	public Nucleus( final int id, final double[] location, final ViewDataBeads myView ) 
	{
		super( id, location, myView );
	}

	public void setDiameter( final double diameter ) { this.diameter = diameter; }
	public double getDiameter() { return diameter; }
	
	Object assignedObject;
	public void setAssignedObject( final Object o ) { this.assignedObject = o; }
	public Object getAssignedObject() { return this.assignedObject; }

	public void set( final double v, final int k ) 
	{
		if ( useW )
			w[ k ] = v;
		else
			l[ k ] = v;
	}	

	@Override
	public String toString()
	{
		String desc = "Nucleus " + getID() + " l"+ Util.printCoordinates( getL() ) + "; w"+ Util.printCoordinates( getW() );
		
		if ( myView != null)
			return desc + " of view " + myView;
		else
			return desc + " - no view assigned";
	}

	@Override
	public Nucleus[] createArray( final int n ){ return new Nucleus[ n ];	}

	public boolean equals( final Nucleus o )
	{
		if ( useW )
		{
			for ( int d = 0; d < 3; ++d )
				if ( w[ d ] != o.w[ d ] )
					return false;			
		}
		else
		{
			for ( int d = 0; d < 3; ++d )
				if ( l[ d ] != o.l[ d ] )
					return false;						
		}
				
		return true;
	}
	
	public static boolean equals( final Nucleus nucleus1, final Nucleus nucleus2 )
	{
		if ( nucleus1.getID() == nucleus2.getID() && nucleus1.getViewID() == nucleus2.getViewID() )
			return true;
		else
			return false;
	}
	
	public boolean isTrueCorrespondence = false;
	public boolean isFalseCorrespondence = false;
	public boolean isAmbigous = false;
	public boolean isUnique = false;
	public int numCorr = 0;

	@Override
	public NucleusIdentification createIdentification() 
	{
		return new NucleusIdentification( this );
	}
}
