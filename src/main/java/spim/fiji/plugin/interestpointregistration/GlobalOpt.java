package spim.fiji.plugin.interestpointregistration;

import java.util.ArrayList;

import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.mpicbg.PointMatchGeneric;

public class GlobalOpt
{
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
