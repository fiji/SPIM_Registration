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
package mpicbg.icp;


import fiji.util.KDTree;
import fiji.util.NearestNeighborSearch;
import fiji.util.node.Leaf;

import java.util.ArrayList;
import java.util.List;

import mpicbg.models.Point;
import mpicbg.models.PointMatch;

public class SimplePointMatchIdentification < P extends Point & Leaf<P> > implements PointMatchIdentification<P>
{
	double distanceThresold;

	public SimplePointMatchIdentification( final double distanceThreshold )
	{
		this.distanceThresold = distanceThreshold;
	}

	public SimplePointMatchIdentification()
	{
		this.distanceThresold = Double.MAX_VALUE;
	}

	public void setDistanceThreshold( final double distanceThreshold ) { this.distanceThresold = distanceThreshold; }
	public double getDistanceThreshold() { return this.distanceThresold; }

	@Override
	public ArrayList<PointMatch> assignPointMatches( final List<P> target, final List<P> reference )
	{
		final ArrayList<PointMatch> pointMatches = new ArrayList<PointMatch>();

		final KDTree<P> kdTreeTarget = new KDTree<P>( target );
		final NearestNeighborSearch<P> nnSearchTarget = new NearestNeighborSearch<P>( kdTreeTarget );

		for ( final P point : reference )
		{
			final P correspondingPoint = nnSearchTarget.findNearestNeighbor( point );

			if ( correspondingPoint.distanceTo( point ) <= distanceThresold )
				pointMatches.add( new PointMatch ( correspondingPoint, point ) );
		}

		return pointMatches;
	}
}
