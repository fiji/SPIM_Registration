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
package mpicbg.pointdescriptor.test;

import fiji.util.node.Leaf;
import mpicbg.models.Point;

public class VirtualPointNode<P extends Point> implements Leaf<VirtualPointNode<P>>
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	final P p;
	final int numDimensions;
	
	public VirtualPointNode( final P p )
	{
		this.p = p;
		this.numDimensions = p.getL().length;
	}
	
	public P getPoint() { return p; }

	@Override
	public float distanceTo( final VirtualPointNode<P> other ) { return (float)Point.distance( p, other.getPoint() ); }

	@Override
	public float get( final int k ) { return (float)p.getW()[ k ]; }

	@Override
	public int getNumDimensions() { return numDimensions; }

	@Override
	public boolean isLeaf() { return true; }

	@Override
	public VirtualPointNode<P>[] createArray( final int n ) { return new VirtualPointNode[ n ]; }
}
