/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2022 Fiji developers.
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

import mpicbg.models.Point;
import mpicbg.models.PointMatch;

public class PointMatchGeneric <P extends Point> extends PointMatch
{
	private static final long serialVersionUID = 1L;

	public PointMatchGeneric( P p1, P p2, double[] weights, double strength )
	{
		super( p1, p2, weights, strength );
	}
	
	public PointMatchGeneric( P p1, P p2, double[] weights )
	{
		super( p1, p2, weights );
	}
	
	public PointMatchGeneric( P p1, P p2, double weight )
	{
		super( p1, p2, weight );
	}

	public PointMatchGeneric( P p1, P p2, double weight, double strength )
	{
		super( p1, p2, weight, strength );
	}
	
	public PointMatchGeneric( P p1, P p2 )
	{
		super( p1, p2 );
	}
	
	final public P getPoint1() { return (P)getP1(); }
	
	final public P getPoint2() { return (P)getP2(); }
	
}
