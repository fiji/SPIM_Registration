package spim.process.interestpointregistration.global.pointmatchcreating.weak;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.Affine3D;
import mpicbg.models.Model;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

/**
 * Uses the previous state as an approximate knowledge for the metadata of the acquisition. New Groups (connected through strong links) will be transformed together,
 * and are mapped back in average to their positions before the global opt run that aligned them relative to each other.
 */
public class FinalMetaDataWeakLinkCreator< N extends Model< N > > extends WeakLinkPointMatchCreator< N >
{
	final ViewRegistrations viewRegistrations;
	HashMap< ViewId, AffineGet > relativeTransforms;

	public FinalMetaDataWeakLinkCreator(
			final HashMap< ViewId, Tile< N > > models1,
			final ViewRegistrations viewRegistrations )
	{
		super( models1 );

		this.viewRegistrations = viewRegistrations;
	}

	@Override
	public < M extends Model< M > > void assignPointMatches(
			final HashMap< ViewId, Tile< M > > models2,
			final ArrayList< Group< ViewId > > groups,
			final Collection< ViewId > fixedViews )
	{
		final ArrayList< ViewId > views = new ArrayList<>( this.allViews );
		Collections.sort( views );

		final HashMap< ViewId, Group< ViewId > > groupMap = new HashMap<>();
A:		for ( final ViewId v : views )
			for ( final Group< ViewId > group : groups )
				if ( group.contains( v ) )
				{
					groupMap.put( v, group );
					continue A;
				}

		// HOW do we express models1 for the second round?
		//
		// a) every view is represented by its corner pixels
		// b) a transformation is expressed as the transformed location of those corners

		// we use the state before the first round of optimization to define PointMatches
		// between all views. These matches are the location of the corners of any other view
		// relative to the current view for whom we make the pointmatches.1

		// compute and save the transformations that we apply to the pointmatches
		// relativeTransforms == result from first global opt PLUS avgMapBack
		this.relativeTransforms = new HashMap<>();
		final HashMap< ViewId, AffineGet > fullTransforms = new HashMap<>();

		for ( final ViewId viewId : views )
		{
			final Affine3D< ? > tilemodel = (Affine3D< ? >)this.models1.get( viewId ).getModel();
			final double[][] m = new double[ 3 ][ 4 ];
			tilemodel.toMatrix( m );
			
			final AffineTransform3D firstRunTransform = new AffineTransform3D();
			firstRunTransform.set(
					m[0][0], m[0][1], m[0][2], m[0][3],
					m[1][0], m[1][1], m[1][2], m[1][3],
					m[2][0], m[2][1], m[2][2], m[2][3] );

			firstRunTransform.preConcatenate( groupMapback.get( viewId ) );

			// this is the relative update from the first global opt run combined with the average mapback model
			this.relativeTransforms.put( viewId, firstRunTransform );

			// get the current status from the ViewRegistrations (the METADATA)
			final ViewRegistration vr = viewRegistrations.getViewRegistration( viewId );
			vr.updateModel();
			//final AffineTransform3D oldGlobalCoordinates = vr.getModel().copy();
			final AffineTransform3D oldGlobalCoordinates = new AffineTransform3D();

			// combine this "old" transformation with the relative update
			fullTransforms.put( viewId, oldGlobalCoordinates.preConcatenate( firstRunTransform ) );
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
					// we use the full transformations (METADATA + 1st run global opt + average Mapback)
					// to transform the input for the 2nd run of global opt
					final AffineGet modelA = fullTransforms.get( vA );
					final AffineGet modelB = fullTransforms.get( vB );

					addPointMatches( modelA, modelB, models2.get( vA ), models2.get( vB ) );
				}
			}
	}

	@Override
	public Map< ViewId, AffineGet > getRelativeTransforms() { return relativeTransforms; }

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
			modelB.applyInverse( pa[i], p[i] );
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
	public < M extends Model< M > > void assignWeights(
			HashMap< ViewId, Tile< M > > tileMap,
			ArrayList< Group< ViewId > > groups,
			Collection< ViewId > fixedViews )
	{
		return;
	}

	public static < M extends Model< M > > AffineTransform3D averageMapBackTransform(
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
