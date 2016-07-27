package spim.process.interestpointregistration.centerofmass;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.Detection;
import spim.process.interestpointregistration.PairwiseMatch;

public class CenterOfMassPairwise implements Callable< PairwiseMatch >
{
	final PairwiseMatch pair;
	final String description;
	final int centerType;

	public CenterOfMassPairwise( final PairwiseMatch pair, final int centerType, final String description )
	{
		this.pair = pair;
		this.centerType = centerType;
		this.description = description;
	}

	@Override
	public PairwiseMatch call()
	{
		if ( pair.getListA().size() < 1 || pair.getListB().size() < 1 )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): " + description + ": "
					+ "Not enough detections to match (1  required per list, |listA|= " +
					pair.getListA().size() + ", |listB|= " + pair.getListB().size() + ")" );
			pair.setCandidates( new ArrayList< PointMatchGeneric< Detection > >() );
			pair.setInliers( new ArrayList< PointMatchGeneric< Detection > >(), Double.NaN );
			return pair;
		}

		final double[] centerA, centerB;

		if ( centerType == 0 )
		{
			centerA = average( pair.getListA() );
			centerB = average( pair.getListB() );
		}
		else
		{
			centerA = median( pair.getListA() );
			centerB = median( pair.getListB() );
		}

		final ArrayList< PointMatchGeneric< Detection > > inliers = new ArrayList< PointMatchGeneric< Detection > >();

		inliers.add( new PointMatchGeneric< Detection >( new Detection( 0, centerA ), new Detection( 0, centerB ) ) );

		pair.setCandidates( inliers );
		pair.setInliers( inliers, 0 );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): " + description +
				": Center A: " + Util.printCoordinates( centerA ) + "Center B: " + Util.printCoordinates( centerB ) );

		return pair;
	}

	private static final double[] average( final List< InterestPoint > list )
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

	private static final double[] median( final List< InterestPoint > list )
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
