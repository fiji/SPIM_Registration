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
package mpicbg.spim.mpicbg;

import spim.vecmath.Point3d;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.NoninvertibleModelException;

final public class Java3d
{
	final public static void applyInPlace( final AbstractAffineModel3D<?> m, final Point3d p )
	{
		final double[] tmp = new double[ 3 ];
		
		tmp[ 0 ] = p.x;
		tmp[ 1 ] = p.y;
		tmp[ 2 ] = p.z;
		
		m.applyInPlace( tmp );
		
		p.x = tmp[ 0 ];
		p.y = tmp[ 1 ];
		p.z = tmp[ 2 ];
	}

	final public static void applyInPlace( final AbstractAffineModel3D<?> m, final Point3d p, final double[] tmp )
	{
		tmp[ 0 ] = p.x;
		tmp[ 1 ] = p.y;
		tmp[ 2 ] = p.z;
		
		m.applyInPlace( tmp );
		
		p.x = tmp[ 0 ];
		p.y = tmp[ 1 ];
		p.z = tmp[ 2 ];
	}
	
	final public static void applyInverseInPlace( final AbstractAffineModel3D<?> m, final Point3d p ) throws NoninvertibleModelException
	{
		final double[] tmp = new double[ 3 ];
		
		tmp[ 0 ] = p.x;
		tmp[ 1 ] = p.y;
		tmp[ 2 ] = p.z;
		
		m.applyInverseInPlace( tmp );
		
		p.x = tmp[ 0 ];
		p.y = tmp[ 1 ];
		p.z = tmp[ 2 ];
	}
	
	final public static void applyInverseInPlace( final AbstractAffineModel3D<?> m, final Point3d p, final double[] tmp ) throws NoninvertibleModelException
	{
		tmp[ 0 ] = p.x;
		tmp[ 1 ] = p.y;
		tmp[ 2 ] = p.z;
		
		m.applyInverseInPlace( tmp );
		
		p.x = tmp[ 0 ];
		p.y = tmp[ 1 ];
		p.z = tmp[ 2 ];
	}
	
}
