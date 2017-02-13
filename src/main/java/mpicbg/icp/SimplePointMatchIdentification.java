package mpicbg.icp;

import java.util.ArrayList;
import java.util.List;

import mpicbg.models.Point;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;

public class SimplePointMatchIdentification < P extends Point & RealLocalizable > implements PointMatchIdentification< P >
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
	public ArrayList< PointMatchGeneric< P > > assignPointMatches( final List< P > target, final List< P > reference )
	{
		final ArrayList< PointMatchGeneric< P > > pointMatches = new ArrayList<>();

		final KDTree< P > kdTreeTarget = new KDTree< P >( target, target );
		final NearestNeighborSearchOnKDTree< P > nnSearchTarget = new NearestNeighborSearchOnKDTree<>( kdTreeTarget );

		for ( final P point : reference )
		{
			nnSearchTarget.search( point );
			final P correspondingPoint = nnSearchTarget.getSampler().get();

			// world coordinates of point
			if ( Point.distance( correspondingPoint, point ) <= distanceThresold )
				pointMatches.add( new PointMatchGeneric< P >( correspondingPoint, point ) );
		}

		return pointMatches;
	}
}
