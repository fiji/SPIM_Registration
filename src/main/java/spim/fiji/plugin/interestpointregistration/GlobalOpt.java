package spim.fiji.plugin.interestpointregistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;

public class GlobalOpt
{
	public static < M extends Model< M > > HashMap< ViewId, Tile< M > > globalOptimization( final M model, final ArrayList< ViewId > views, final ArrayList< ChannelInterestPointListPair > pairs )
	{
		// remember the Tiles
		final HashMap< ViewId, Tile< M > > map = new HashMap< ViewId, Tile< M > >();
		
		for ( final ViewId viewId : views )
			map.put( viewId, new Tile<M>( model.copy() ) );

		for ( final ChannelInterestPointListPair pair : pairs )
			GlobalOpt.addPointMatches( pair.getInliers(), map.get( pair.getViewIdA() ), map.get( pair.getViewIdB() ) );
		
		final TileConfiguration tc = new TileConfiguration();
		int fixedTiles = 0;
		
		// fix the first one if possible
		for ( final ViewId viewId : views )
		{
			final Tile< M > tile = map.get( viewId );
			
			if ( tile.getConnectedTiles().size() > 0 )
			{
				tc.addTile( tile );
				if ( fixedTiles == 0 )
				{					
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fixing tile (viewSetupId = " + viewId.getViewSetupId() + ")" ); 
					tc.fixTile( tile );
					++fixedTiles;
				}
			}
		}
		
		try 
		{
			int unaligned = tc.preAlign().size();
			if ( unaligned > 0 )
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): pre-aligned all tiles but " + unaligned );
			else
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): prealigned all tiles" );
			
			tc.optimize( 10, 10000, 200 );
			
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Global optimization of " + views.size() +  " tiles:" );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Avg Error: " + tc.getError() + "px" );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Min Error: " + tc.getMinError() + "px" );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Max Error: " + tc.getMaxError() + "px" );
		} catch (NotEnoughDataPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllDefinedDataPointsException e) {
			// TODO Auto-generated catch block
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

	public static void addPointMatches( final ArrayList< PointMatchGeneric< Detection > > correspondences, final Tile<?> tileA, final Tile<?> tileB )
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
}
