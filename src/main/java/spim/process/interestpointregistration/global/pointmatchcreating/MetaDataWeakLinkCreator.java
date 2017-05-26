package spim.process.interestpointregistration.global.pointmatchcreating;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.process.interestpointregistration.TransformationTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

/**
 * Uses the previous state as an approximate knowledge for the metadata of the acquisition. New Groups (connected through strong links) will be transformed together,
 * and are mapped back in average to their positions before the global opt run that aligned them relative to each other.
 */
public class MetaDataWeakLinkCreator< N extends Model< N > > extends WeakLinkPointMatchCreator< N >
{
	final ViewRegistrations viewRegistrations;

	public MetaDataWeakLinkCreator(
			final ArrayList< Group< ViewId > > groupsNew,
			final HashMap< ViewId, Tile< N > > models,
			final ViewRegistrations viewRegistrations )
	{
		super( groupsNew, models );

		this.viewRegistrations = viewRegistrations;
	}

	@Override
	public < M extends Model< M > > void assignPointMatches(
			final HashMap< ViewId, Tile< M > > tileMap,
			final ArrayList< Group< ViewId > > groups,
			final Collection< ViewId > fixedViews )
	{
		final ArrayList< ViewId > views = new ArrayList<>( tileMap.keySet() );
		Collections.sort( views );

		final HashMap< ViewId, Group< ViewId > > groupMap = new HashMap<>();
A:		for ( final ViewId v : views )
			for ( final Group< ViewId > group : groupsNew )
				if ( group.contains( v ) )
				{
					groupMap.put( v, group );
					break A;
				}

		// compute an average affine mapback transform for each new group (which was not in the same group for the first global opt run)
		final HashMap< Group< ViewId >, AffineGet > groupMapback = new HashMap<>();
		for ( final Group< ViewId > group : groupsNew )
			groupMapback.put( group, averageMapBackTransform( group, models ) );

		for ( final ViewId viewId : views )
		{
			// TODO: do this temporarily here
			final ViewRegistration vr = viewRegistrations.getViewRegistration( viewId );
			TransformationTools.storeTransformation( vr, viewId, tileMap.get( viewId ), groupMapback.get( viewId ), "Applying Strong Links" );
		}

		for ( int a = 0; a < views.size() - 1; ++a )
			for ( int b = a + 1; b < views.size(); ++b )
			{
				final ViewId vA = views.get( a );
				final ViewId vB = views.get( b );

				final Group< ViewId > gA = groupMap.get( vA );
				final Group< ViewId > gB = groupMap.get( vB );

				if ( !gA.equals( gB ) ) // TODO: Test overlay?
				{
					// not in the same group, so we need weak links
					final ViewRegistration vrA = viewRegistrations.getViewRegistration( vA );
					final ViewRegistration vrB = viewRegistrations.getViewRegistration( vB );

					final AffineTransform3D modelA = vrA.getModel();
					final AffineTransform3D modelB = vrB.getModel();

					addPointMatches( modelA, modelB, tileMap.get( vA ), tileMap.get( vB ) );
				}
			}
	}

	public static <M extends Model< M >> void addPointMatches( 
			final AffineGet modelA,
			final AffineGet modelB,
			final Tile<M> tileA,
			final Tile<M> tileB )
	{
		final ArrayList< PointMatch > pm = new ArrayList< PointMatch >();
		final List<Point> pointsA = new ArrayList<>();
		final List<Point> pointsB = new ArrayList<>();

		// we use the vertices of the unit cube and their transformations as point matches 
		final double[][] p = new double[][]{
			{ 0, 0, 0 },
			{ 1, 0, 0 },
			{ 0, 1, 0 },
			{ 1, 1, 0 },
			{ 0, 0, 1 },
			{ 1, 0, 1 },
			{ 0, 1, 1 },
			{ 1, 1, 1 }};

		final double[][] pa = new double[8][3];
		final double[][] pb = new double[8][3];

		// the transformed bounding boxes are our corresponding features
		for (int i = 0; i < pa.length; ++i)
		{
			modelA.applyInverse( pb[i], p[i] );
			modelA.applyInverse( pa[i], p[i] );
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

	@Override
	public HashSet< ViewId > getAllViews()
	{
		final HashSet< ViewId > set = new HashSet<>();

		for ( final Group< ViewId > group : groupsNew )
			set.addAll( group.getViews() );

		return set;
	}

	@Override
	public < M extends Model< M > > void assignWeights(
			HashMap< ViewId, Tile< M > > tileMap,
			ArrayList< Group< ViewId > > groups,
			Collection< ViewId > fixedViews )
	{
		return;
	}

	public static < M extends Model< M > > AffineGet averageMapBackTransform(
			final Group< ViewId > group,
			final HashMap< ViewId, Tile< M > > models )
	{
		final double[] sum = new double[ 12 ];
		final double[] tmp = new double[ 12 ];
		
		for ( ViewId viewId : group )
		{
			((AbstractAffineModel3D< ? >)models.get( viewId ).getModel()).toArray( tmp );
			for ( int i = 0; i < sum.length; ++i )
				sum[ i ] += tmp[ i ];
		}

		for ( int i = 0; i < sum.length; ++i )
			sum[ i ] /= (double)group.size();

		AffineTransform3D affine = new AffineTransform3D();
		affine.set(
				sum[ 0 ], sum[ 3 ], sum[ 6 ], sum[ 9 ],
				sum[ 1 ], sum[ 4 ], sum[ 7 ], sum[ 10 ],
				sum[ 2 ], sum[ 5 ], sum[ 8 ], sum[ 11 ] );

		return affine.inverse();
	}

}
