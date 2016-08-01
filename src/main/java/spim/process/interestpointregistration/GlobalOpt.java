package spim.process.interestpointregistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;
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
	 * @param registrationType - to determine which tiles are fixed
	 * @param subset - to get the correspondences
	 * @return - list of Tiles containing the final transformation models
	 */
	public static < M extends Model< M > > HashMap< ViewId, Tile< M > > compute(
			final M model,
			final GlobalOptimizationType registrationType,
			final GlobalOptimizationSubset subset,
			final boolean considerTimePointsAsUnit )
	{
		// assemble all views and corresponding points
		final List< PairwiseMatch > pairs = subset.getViewPairs();
		final List< ViewId > views = subset.getViews();
		
		// assign ViewIds to the individual Tiles (either one tile per view or one tile per timepoint)
		final HashMap< ViewId, Tile< M > > map = assignViewsToTiles( model, views, considerTimePointsAsUnit );

		// assign the pointmatches to all the tiles
		for ( final PairwiseMatch pair : pairs )
			GlobalOpt.addPointMatches( pair.getInliers(), map.get( pair.getViewIdA() ), map.get( pair.getViewIdB() ) );

		// add and fix tiles as defined in the GlobalOptimizationType
		final TileConfiguration tc = addAndFixTiles( views, map, registrationType, subset, considerTimePointsAsUnit );
		
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
			
			if ( considerTimePointsAsUnit )
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Global optimization of " + 
					tc.getTiles().size() +  " timepoint-tiles (Model=" + model.getClass().getSimpleName()  + "):" );
			else
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
			
			String output = "ViewId=" + viewId.getViewSetupId() + ": " + printAffine3D( (Affine3D<?>)tile.getModel() );
			
			if ( (Model)tile.getModel() instanceof RigidModel3D )
			{
				String rotA = "Java3D is MISSING!";

				try
				{
					rotA = getRotationAxis( (RigidModel3D)(Model)tile.getModel() );
				}
				catch( NoClassDefFoundError e ) {}

				IOFunctions.println( output + ", Rotation Axis=" + rotA );
				
			}
			else
			{
				String scaling = "Java3D is MISSING!";

				try
				{
					scaling = getScaling( (Affine3D<?>)tile.getModel() );
				}
				catch( NoClassDefFoundError e ) {}

				IOFunctions.println( output + ", Scaling=" + scaling );
			}
		}
		
		return map;
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
			final GlobalOptimizationType registrationType,
			final GlobalOptimizationSubset subset,
			final boolean considerTimePointsAsUnit )
	{
		// create a new tileconfiguration organizing the global optimization
		final TileConfiguration tc = new TileConfiguration();
		
		// assemble a list of all tiles and set them fixed if desired
		final HashSet< Tile< M > > tiles = new HashSet< Tile< M > >();
		
		for ( final ViewId viewId : views )
		{
			final Tile< M > tile = map.get( viewId );

			// if one of the views that maps to this tile is fixed, fix this tile if it is not already fixed
			if ( registrationType.isFixedTile( viewId ) && !tc.getFixedTiles().contains( tile ) )
			{
				if ( considerTimePointsAsUnit )
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fixing timepoint-tile (timepointId = " + viewId.getTimePointId() + ")" );
				else
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fixing view-tile (viewSetupId = " + viewId.getViewSetupId() + ")" );
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
			final boolean considerTimePointsAsUnit )
	{
		final HashMap< ViewId, Tile< M > > map = new HashMap< ViewId, Tile< M > >();
		
		if ( considerTimePointsAsUnit )
		{
			//
			// there is one tile per timepoint
			//
			
			// figure out all timepoints involved
			final HashSet< Integer > timepoints = new HashSet< Integer >();
			
			for ( final ViewId view : views )
				timepoints.add( view.getTimePointId() );
			
			// for all timepoints find the viewIds that belong to this timepoint
			for ( final int t : timepoints )
			{
				// one tile per timepoint
				final Tile< M > tileTimepoint = new Tile< M >( model.copy() );

				// all viewIds of one timepoint map to the same tile (see main method for test, that works)
				for ( final ViewId viewId : views )
					if ( viewId.getTimePointId() == t )
						map.put( viewId, tileTimepoint );
			}
		}
		else
		{
			// there is one tile per view
			for ( final ViewId viewId : views )
				map.put( viewId, new Tile< M >( model.copy() ) );		
		}
		
		return map;
	}

	protected static void addPointMatches( final ArrayList< PointMatchGeneric< Detection > > correspondences, final Tile<?> tileA, final Tile<?> tileB )
	{
		final ArrayList<PointMatch> pm = new ArrayList<PointMatch>();
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
