package spim.process.interestpointregistration.pairwise;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.headless.registration.centerofmass.CenterOfMassParameters;
import spim.process.interestpointregistration.Detection;

public class CenterOfMassPairwise implements MatcherPairwise
{
	final CenterOfMassParameters params;

	public CenterOfMassPairwise( final CenterOfMassParameters params )
	{
		this.params = params;
	}

	@Override
	public PairwiseResult match( final List< ? extends InterestPoint > listAIn, final List< ? extends InterestPoint > listBIn )
	{
		final PairwiseResult result = new PairwiseResult();

		if ( listAIn == null || listBIn == null || listAIn.size() < 1 || listBIn.size() < 1 )
		{
			result.setCandidates( new ArrayList< PointMatchGeneric< Detection > >() );
			result.setInliers( new ArrayList< PointMatchGeneric< Detection > >(), Double.NaN );

			result.setResult(
				System.currentTimeMillis(),
				"Not enough detections to match (1  required per list, |listA|= " + listAIn.size() + ", |listB|= " + listBIn.size() + ")" );
			return result;
		}

		final double[] centerA, centerB;

		if ( params.getCenterType() == 0 )
		{
			centerA = average( listAIn );
			centerB = average( listBIn );
		}
		else
		{
			centerA = median( listAIn );
			centerB = median( listBIn );
		}

		final ArrayList< PointMatchGeneric< Detection > > inliers = new ArrayList< PointMatchGeneric< Detection > >();

		inliers.add( new PointMatchGeneric< Detection >( new Detection( 0, centerA ), new Detection( 0, centerB ) ) );

		result.setCandidates( inliers );
		result.setInliers( inliers, 0 );

		result.setResult(
				System.currentTimeMillis(),
				"Center of Mass, Center A: " + Util.printCoordinates( centerA ) + ", Center B: " + Util.printCoordinates( centerB ) );

		return result;
	}

	private static final double[] average( final List< ? extends InterestPoint > list )
	{
		final int n = list.get( 0 ).getL().length;
		final RealSum[] sum = new RealSum[ n ];

		for ( int d = 0; d < n; ++d )
			sum[ d ] = new RealSum();

		for ( final InterestPoint i : list )
		{
			final double[] l = i.getL();

			for ( int d = 0; d < n; ++d )
				sum[ d ].add( l[ d ] );
		}

		final double[] center = new double[ n ];

		for ( int d = 0; d < n; ++d )
			center[ d ] = sum[ d ].getSum() / (double)list.size();

		return center;
	}

	private static final double[] median( final List< ? extends InterestPoint > list )
	{
		final int n = list.get( 0 ).getL().length;
		final double[][] values = new double[ n ][ list.size() ];

		for ( int j = 0; j < list.size(); ++j )
		{
			final double[] l = list.get( j ).getL();

			for ( int d = 0; d < n; ++d )
				values[ d ][ j ] = l[ d ];
		}

		final double[] center = new double[ n ];

		for ( int d = 0; d < n; ++d )
			center[ d ] = Util.median( values[ d ] );

		return center;
	}
}
