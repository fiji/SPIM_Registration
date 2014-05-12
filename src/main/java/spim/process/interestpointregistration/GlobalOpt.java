package spim.process.interestpointregistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import mpicbg.models.AffineModel3D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;

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
		final ArrayList< ChannelInterestPointListPair > pairs = subset.getViewPairs();
		final ArrayList< ViewId > views = subset.getViews();
		
		// assign ViewIds to the individual Tiles (either one tile per view or one tile per timepoint)
		final HashMap< ViewId, Tile< M > > map = assignViewsToTiles( model, views, considerTimePointsAsUnit );

		// assign the pointmatches to all the tiles
		for ( final ChannelInterestPointListPair pair : pairs )
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

		for ( final ViewId viewId : views )
		{
			final Tile< M > tile = map.get( viewId );	
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): ViewId=" + viewId.getViewSetupId() + ": " +  tile.getModel() );
		}
		
		return map;
	}
	
	protected static < M extends Model< M > > TileConfiguration addAndFixTiles(
			final ArrayList< ViewId > views,
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
			if ( registrationType.isFixedTile( viewId, subset ) && !tc.getFixedTiles().contains( tile ) )
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
			final ArrayList< ViewId > views,
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
