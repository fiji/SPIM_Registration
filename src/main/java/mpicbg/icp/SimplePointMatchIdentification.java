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
