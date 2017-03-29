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
package mpicbg.spim.mpicbg;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.detection.AbstractDetection;

public class TileSPIM< M extends AbstractAffineModel3D<M> > extends Tile<M> 
{
	/**
	 * Constructor
	 * 
	 * @param model the transformation {@link Model} of the {@link Tile}.
	 */
	public TileSPIM( final M model, final ViewDataBeads parent )
	{
		super( model );
		this.parent = parent;
	}

	final protected ViewDataBeads parent;
	
	public ViewDataBeads getParent(){ return parent; }

	/**
	 * Apply the current {@link Model} to all local point coordinates.
	 * Update {@link #cost} and {@link #distance}.
	 *
	 */
	final public void updateWithDections()
	{
		// call the original method
		update();
		
		if ( matches.size() > 0 )
		{
			for ( final PointMatch match : matches )
			{
				final double dl = match.getDistance();
				((AbstractDetection<?>)match.getP1()).setDistance( (float)dl );
				((AbstractDetection<?>)match.getP2()).setDistance( (float)dl );				
			}
		}

	}
}
