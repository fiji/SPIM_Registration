package spim.process.interestpointregistration.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.Affine3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel3D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.util.Pair;
import spim.process.interestpointregistration.pairwise.PairwiseResult;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.vecmath.Matrix4f;
import spim.vecmath.Quat4f;
import spim.vecmath.Transform3D;
import spim.vecmath.Vector3d;
import spim.vecmath.Vector3f;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class GlobalOpt
{
	/**
	 * Computes a global optimization based on the corresponding points
	 * 
	 */
	public static < M extends Model< M > > HashMap< ViewId, Tile< M > > compute(
			final M model,
			final List< ? extends Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > > pairs,
			final Collection< ViewId > fixedViews,
			final Set< Group< ViewId > > groupsIn )
	{
		// merge overlapping groups if necessary
		final ArrayList< Group< ViewId > > groups = Group.mergeAllOverlappingGroups( groupsIn );

		// remove empty groups
		Group.removeEmptyGroups( groups );

		// assemble all views
		final HashSet< ViewId > tmpSet = new HashSet<>();
		for ( Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > pair : pairs )
		{
			tmpSet.add( pair.getA().getA() );
			tmpSet.add( pair.getA().getB() );
		}

		// views that are part of a group but not of a pair and will thus be transformed as well
		for ( final Group< ViewId > group : groups )
				tmpSet.addAll( group.getViews() );

		final List< ViewId > views = new ArrayList< ViewId >();
		views.addAll( tmpSet );
		Collections.sort( views );

		// assign ViewIds to the individual Tiles (either one tile per view or one tile per group)
		final HashMap< ViewId, Tile< M > > map = assignViewsToTiles( model, views, groups );

		// assign weights per group
		assignWeights( pairs, groups, map );

		// assign the pointmatches to all the tiles
		for ( Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > pair : pairs )
			GlobalOpt.addPointMatches( pair.getB().getInliers(), map.get( pair.getA().getA() ), map.get( pair.getA().getB() ) );

		// add and fix tiles as defined in the GlobalOptimizationType
		final TileConfiguration tc = addAndFixTiles( views, map, fixedViews, groups );
		
		if ( tc.getTiles().size() == 0 )
		{
			IOFunctions.println( "There are no connected tiles, cannot do an optimization. Quitting." );
			return null;
		}
		
		// now perform the global optimization
		try 
		{
			int unaligned = tc.preAlign().size();
			if ( unaligned > 0 )
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): pre-aligned all tiles but " + unaligned );
			else
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): prealigned all tiles" );

			tc.optimize( 10, 10000, 200 );

			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Global optimization of " + 
				tc.getTiles().size() +  " view-tiles (Model=" + model.getClass().getSimpleName()  + "):" );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Avg Error: " + tc.getError() + "px" );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Min Error: " + tc.getMinError() + "px" );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Max Error: " + tc.getMaxError() + "px" );
		}
		catch (NotEnoughDataPointsException e)
		{
			IOFunctions.println( "Global optimization failed: " + e );
			e.printStackTrace();
		}
		catch (IllDefinedDataPointsException e)
		{
			IOFunctions.println( "Global optimization failed: " + e );
			e.printStackTrace();
		}
		
		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Transformation Models:" );

		// TODO: We assume it is Affine3D here
		for ( final ViewId viewId : views )
		{
			final Tile< M > tile = map.get( viewId );
			
			String output = Group.pvid( viewId ) + ": " + printAffine3D( (Affine3D<?>)tile.getModel() );
			
			if ( (Model)tile.getModel() instanceof RigidModel3D )
				IOFunctions.println( output + ", " + getRotationAxis( (RigidModel3D)(Model)tile.getModel() ) );
			else
				IOFunctions.println( output + ", " + getScaling( (Affine3D<?>)tile.getModel() ) );
		}
		
		return map;
	}

	private static < M extends Model< M > >  void assignWeights(
			final List< ? extends Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > > pairs,
			final ArrayList< Group< ViewId > > groups,
			final HashMap< ViewId, Tile< M > > tileMap )
	{
		final HashMap< Group< ViewId >, Integer > groupCount = new HashMap<>();
		final HashMap< ViewId, Integer > viewCount = new HashMap<>();
		final HashMap< ViewId, Group< ViewId > > viewGroupAssign = new HashMap<>();

		for ( final Group< ViewId > group : groups )
			groupCount.put( group, 0 );

		for ( final ViewId viewId : tileMap.keySet() )
			viewCount.put( viewId, 0 );

		// find out inliers per view, and sum of inliers per group
		for ( Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > pair : pairs )
		{
			final ViewId vA = pair.getA().getA();
			final ViewId vB = pair.getA().getB();

			final int numInliers = pair.getB().getInliers().size();

			viewCount.put( vA, viewCount.get( vA ) + numInliers );
			viewCount.put( vB, viewCount.get( vB ) + numInliers );

			for ( final Group< ViewId > group : groups )
			{
				if ( group.contains( vA ) )
				{
					groupCount.put( group, groupCount.get( group ) + numInliers );
					viewGroupAssign.put( vA, group );
				}

				if ( group.contains( vB ) )
				{
					groupCount.put( group, groupCount.get( group ) + numInliers );
					viewGroupAssign.put( vB, group );
				}
			}
		}

		final HashMap< ViewId, Double > ratio = new HashMap<>();
		final HashMap< Group< ViewId >, Double > maxGroupRatio = new HashMap<>();

		// find the ratio (inliers/groupInliers) per view, or 1.0 if it is not part of a group (i.e. one view per group)
		for ( final ViewId viewId : tileMap.keySet() )
		{
			final Group< ViewId > group = viewGroupAssign.get( viewId );

			if ( group != null )
			{
				final double numCorr = viewCount.get( viewId );
				final double numCorrGroup = groupCount.get( group );
				ratio.put( viewId, numCorr / numCorrGroup );

				double maxRatio;

				if ( maxGroupRatio.containsKey( group ) )
					maxRatio = maxGroupRatio.get( group );
				else
					maxRatio = -1;

				maxGroupRatio.put( group, Math.max( maxRatio, numCorr / numCorrGroup ) );
			}
			else
			{
				ratio.put( viewId, 1.0 );
			}
		}

		final ArrayList< ViewId > views = new ArrayList<>();
		views.addAll( tileMap.keySet() );
		Collections.sort( views );

		// assign a value that reflects how much lower the ratio is per view
		// e.g. view0 = 0.5, view1 = 0.25
		// means view0 = 0.5 and view1 = 2
		for ( final ViewId viewId : views )
		{
			final Group< ViewId > group = viewGroupAssign.get( viewId );
			double maxRatio = 1;
			if ( group != null )
				maxRatio = maxGroupRatio.get( group );

			System.out.println( Group.pvid( viewId ) + ": " + ratio.get( viewId ) + " of "  + maxRatio + " >>> " + ( maxRatio/ratio.get( viewId ) ) );

			ratio.put( viewId, ( maxRatio/ratio.get( viewId ) ) );
		}

		// assign the max of each value to the pointmatches between two views
		for ( final Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > pair : pairs )
		{
			final double ratioA = ratio.get( pair.getA().getA() );
			final double ratioB = ratio.get( pair.getA().getB() );

			final double weight = Math.max( ratioA, ratioB );

			for ( final PointMatchGeneric< ? > pm : pair.getB().getInliers() )
				pm.setWeight( 0, weight );

			//System.out.println( Group.pvid( pair.getA().getA() ) + "<->" + Group.pvid( pair.getA().getB() ) + ": " + weight );
		}
	}

	/**
	 * WARNING: This fails on older MACs, in this case remove: 
	 * 
	 * Check if Apple's out-dated Java 3D version 1.3 is installed in System/Library/Java/Extensions/ on your Mac. 
	 * Remove all Java 3D 1.3 related files including vecmath.jar (jar, jnilib), they are useless.
	 * 
	 * @param model
	 * @return
	 */
	public static String getRotationAxis( final RigidModel3D model )
	{
		try
		{
			final Matrix4f matrix = new Matrix4f();
			getTransform3D( model ).get( matrix );
			final Quat4f qu = new Quat4f();
			qu.set( matrix );
			
			final Vector3f n = new Vector3f(qu.getX(),qu.getY(), qu.getZ());
			n.normalize();
			
			return "Approx. axis: " + n + ", approx. angle: " + Math.toDegrees( Math.acos( qu.getW() ) * 2 );
		}
		catch ( Exception e )
		{
			return "Check if Apple's out-dated Java 3D version 1.3 is installed in System/Library/Java/Extensions/ on your Mac." +
					"Remove all Java 3D 1.3 related files including vecmath.jar (jar, jnilib), they are useless.";
		}
	}
	
	public static String getScaling( final Affine3D< ? > affine )
	{
		final Transform3D t = getTransform3D( affine );
		final Vector3d v = new Vector3d();
		
		t.getScale( v );
		
		return "Scaling: " + v.x + ", " + v.y + ", " + v.z;
	}

	public static Transform3D getTransform3D( final Affine3D< ? > affine )
	{
		final double[][] m = new double[ 3 ][ 4 ];
		((Affine3D<?>)affine).toMatrix( m );

		final Transform3D transform = new Transform3D();
		final double[] m2 = new double[ 16 ];
		transform.get( m2 );
		
		m2[ 0 ] = m[0][0];
		m2[ 1 ] = m[0][1];
		m2[ 2 ] = m[0][2];
		m2[ 3 ] = m[0][3];

		m2[ 4 ] = m[1][0];
		m2[ 5 ] = m[1][1];
		m2[ 6 ] = m[1][2];
		m2[ 7 ] = m[1][3];

		m2[ 8 ] = m[2][0];
		m2[ 9 ] = m[2][1];
		m2[ 10] = m[2][2];
		m2[ 11] = m[2][3];

		transform.set( m2 );

		return transform;
	}

	public static <M extends AbstractAffineModel3D<M>> Transform3D getTransform3D( final M model )
	{
		final Transform3D transform = new Transform3D();
		final double[] m = model.getMatrix( null );

		final double[] m2 = new double[ 16 ];
		transform.get( m2 );

		for ( int i = 0; i < m.length; ++i )
			m2[ i ] = m[ i ];

		transform.set( m2 );

		return transform;
	}

	public static String printAffine3D( final Affine3D< ? > model )
	{
		final double[][] m = new double[ 3 ][ 4 ];
		model.toMatrix( m );
		
		return m[0][0] + "," + m[0][1] + "," + m[0][2] + "," + m[0][3] + "," + 
				m[1][0] + "," + m[1][1] + "," + m[1][2] + "," + m[1][3] + "," + 
				m[2][0] + "," + m[2][1] + "," + m[2][2] + "," + m[2][3];
	}
	
	protected static < M extends Model< M > > TileConfiguration addAndFixTiles(
			final List< ViewId > views,
			final HashMap< ViewId, Tile< M > > map,
			final Collection< ViewId > fixedViews,
			final List< ? extends Group < ViewId > > groups )
	{
		// create a new tileconfiguration organizing the global optimization
		final TileConfiguration tc = new TileConfiguration();
		
		// assemble a list of all tiles and set them fixed if desired
		final HashSet< Tile< M > > tiles = new HashSet< Tile< M > >();
		
		for ( final ViewId viewId : views )
		{
			final Tile< M > tile = map.get( viewId );

			// if one of the views that maps to this tile is fixed, fix this tile if it is not already fixed
			if ( fixedViews.contains( viewId ) && !tc.getFixedTiles().contains( tile ) )
			{
				final Group< ViewId > fixedGroup = Group.isContained( viewId, groups );
				if ( fixedGroup != null )
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fixing group-tile [" + fixedGroup + "]" );
				else
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fixing view-tile [" + Group.pvid( viewId ) + "]" );
				tc.fixTile( tile );
			}

			// add it if it is not already there
			tiles.add( tile );
		}	
		
		// now add connected tiles to the tileconfiguration
		for ( final Tile< M > tile : tiles )
			if ( tile.getConnectedTiles().size() > 0 )
				tc.addTile( tile );
		
		return tc;
	}
	
	protected static < M extends Model< M > > HashMap< ViewId, Tile< M > > assignViewsToTiles(
			final M model,
			final List< ViewId > views,
			final List< Group< ViewId > > groups )
	{
		final HashMap< ViewId, Tile< M > > map = new HashMap< ViewId, Tile< M > >();

		if ( groups != null && groups.size() > 0 )
		{
			//
			// we make only mpicbg-Tile per group since all views are transformed together
			//

			// remember those who are not part of a group
			final HashSet< ViewId > remainingViews = new HashSet< ViewId >();
			remainingViews.addAll( views );

			// for all groups find the viewIds that belong to this timepoint
			for ( final Group< ViewId > viewIds : groups )
			{
				// one tile per group
				final Tile< M > tileGroup = new Tile< M >( model.copy() );

				// all viewIds of one group map to the same tile (see main method for test, that works)
				for ( final ViewId viewId : viewIds )
				{
					map.put( viewId, tileGroup );

					// just to make sure that there are no views part of two groups or present twice
					if ( !remainingViews.contains( viewId ) )
						throw new RuntimeException(
								"ViewSetupID:" + viewId.getViewSetupId() + ", timepointId: " + viewId.getTimePointId() +
								" is part of two groups, this is a bug since those should have been merged." ); 

					remainingViews.remove( viewId );
				}
			}

			// add all remaining views
			for ( final ViewId viewId : remainingViews )
				map.put( viewId, new Tile< M >( model.copy() ) );
		}
		else
		{
			// there is one tile per view
			for ( final ViewId viewId : views )
				map.put( viewId, new Tile< M >( model.copy() ) );
		}
		
		return map;
	}

	protected static void addPointMatches( final List< ? extends PointMatchGeneric< ? > > correspondences, final Tile< ? > tileA, final Tile< ? > tileB )
	{
		final ArrayList< PointMatch > pm = new ArrayList<>();
		pm.addAll( correspondences );

		if ( correspondences.size() > 0 )
		{
			tileA.addMatches( pm );
			tileB.addMatches( PointMatch.flip( pm ) );
			tileA.addConnectedTile( tileB );
			tileB.addConnectedTile( tileA );
		}
	}
	
	public static void main( String[] args )
	{
		// multiple keys can map to the same value
		final HashMap< ViewId, Tile< AffineModel3D > > map = new HashMap<ViewId, Tile<AffineModel3D>>();
		
		final AffineModel3D m1 = new AffineModel3D();
		final AffineModel3D m2 = new AffineModel3D();

		final Tile< AffineModel3D > tile1 = new Tile<AffineModel3D>( m1 );
		final Tile< AffineModel3D > tile2 = new Tile<AffineModel3D>( m2 );
		
		final ViewId v11 = new ViewId( 1, 1 );
		final ViewId v21 = new ViewId( 2, 1 );
		final ViewId v12 = new ViewId( 1, 2 );
		final ViewId v22 = new ViewId( 2, 2 );
		
		map.put( v11, tile1 );
		map.put( v21, tile2 );

		map.put( v12, tile1 );
		map.put( v22, tile2 );
		
		m1.set( 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 );
		m2.set( 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 );

		System.out.println( map.get( v11 ).getModel() );
		System.out.println( map.get( v21 ).getModel() );
		
		System.out.println( map.get( v12 ).getModel() );
		System.out.println( map.get( v22 ).getModel() );		
	}
}
