package spim.process.interestpointregistration.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import mpicbg.models.Model;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RealInterval;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.process.interestpointregistration.global.Link.LinkType;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class ImageCorrelationPointMatchCreator implements PointMatchCreator
{
	final Collection< PairwiseStitchingResult< ? extends ViewId > > pairwiseResults;
	final double correlationT;

	public ImageCorrelationPointMatchCreator(
			final Collection< PairwiseStitchingResult< ? extends ViewId > > pairwiseResults,
			final double correlationT )
	{
		this.pairwiseResults = pairwiseResults;
		this.correlationT = correlationT;
	}

	@Override
	public HashSet< ViewId > getAllViews()
	{
		final HashSet< ViewId > tmpSet = new HashSet<>();

		for ( PairwiseStitchingResult< ? extends ViewId > pair : pairwiseResults )
		{
			for ( final ViewId v : pair.pair().getA() )
				tmpSet.add( v );

			for ( final ViewId v : pair.pair().getB() )
				tmpSet.add( v );
		}

		return tmpSet;
	}

	@Override
	public < M extends Model< M > > void assignWeights(
			final HashMap< ViewId, Tile< M > > tileMap,
			final ArrayList< Group< ViewId > > groups,
			final Collection< ViewId > fixedViews )
	{
		return;
	}

	@Override
	public < M extends Model< M > > void assignPointMatches(
			final HashMap< ViewId, Tile< M > > tileMap,
			final ArrayList< Group< ViewId > > groups,
			final Collection< ViewId > fixedViews )
	{
		final List< Link< Group< ? extends ViewId > > > strongLinks = new ArrayList<>();

		for ( final PairwiseStitchingResult< ? extends ViewId > res : pairwiseResults )
		{
			// only consider Pairs that were selected and that have high enough correlation
			if ( res.r() >= correlationT )
			{
				strongLinks.add( new Link< Group< ? extends ViewId > >( res.pair().getA(), res.pair().getB(), res.getBoundingBox(), res.getTransform(), LinkType.STRONG ) );
				System.out.println( "added strong link between " + res.pair().getA() + " and " + res.pair().getB() + ": " + res.getTransform() );
			}
		}

		// assign the pointmatches to all the tiles
		// we just need one of the views, since they all map to the same tile
		for ( Link< Group< ? extends ViewId > > link : strongLinks )
			addPointMatches( link, tileMap.get( link.getFirst().iterator().next() ), tileMap.get( link.getSecond().iterator().next() ) );
	}

	public static <M extends Model< M >> void addPointMatches( 
			final Link<Group<? extends ViewId>> link,
			final Tile<M> tileA,
			final Tile<M> tileB )
	{
		final ArrayList< PointMatch > pm = new ArrayList< PointMatch >();
		final List<Point> pointsA = new ArrayList<>();
		final List<Point> pointsB = new ArrayList<>();

		final RealInterval bb = link.getBoundingBox();

		// we use the vertices of the unit cube and their transformations as point matches 
		final double[][] pa = new double[][]{
			{ bb.realMin( 0 ), bb.realMin( 1 ), bb.realMin( 2 ) },
			{ bb.realMax( 0 ), bb.realMin( 1 ), bb.realMin( 2 ) },
			{ bb.realMin( 0 ), bb.realMax( 1 ), bb.realMin( 2 ) },
			{ bb.realMax( 0 ), bb.realMax( 1 ), bb.realMin( 2 ) },
			{ bb.realMin( 0 ), bb.realMin( 1 ), bb.realMax( 2 ) },
			{ bb.realMax( 0 ), bb.realMin( 1 ), bb.realMax( 2 ) },
			{ bb.realMin( 0 ), bb.realMax( 1 ), bb.realMax( 2 ) },
			{ bb.realMax( 0 ), bb.realMax( 1 ), bb.realMax( 2 ) }};

		final double[][] pb = new double[8][3];

		// the transformed bounding boxes are our corresponding features
		for (int i = 0; i < pa.length; ++i)
		{
			link.getTransform().applyInverse( pb[i], pa[i] );
			pointsA.add( new Point( pa[i] ) );
			pointsB.add( new Point( pb[i] ) );
		}

		// create PointMatches and connect Tiles
		for (int i = 0; i < pointsA.size(); ++i)
			pm.add( new PointMatch( pointsA.get( i ) , pointsB.get( i ) ) );

		tileA.addMatches( pm );
		tileB.addMatches( PointMatch.flip( pm ) );
		tileA.addConnectedTile( tileB );
		tileB.addConnectedTile( tileA );
	}
}
