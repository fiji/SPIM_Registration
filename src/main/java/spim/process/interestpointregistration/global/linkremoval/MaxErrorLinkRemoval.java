package spim.process.interestpointregistration.global.linkremoval;

import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;

public class MaxErrorLinkRemoval implements LinkRemovalStrategy
{
	@Override
	public boolean removeLink( final TileConfiguration tc )
	{
		double worstDistance = -Double.MAX_VALUE;
		Tile<?> worstTile1 = null;
		Tile<?> worstTile2 = null;
		
		for (Tile<?> t : tc.getTiles())
		{
			// we mustn't disconnect a tile entirely
			if (t.getConnectedTiles().size() <= 1)
				continue;
			
			for (PointMatch pm : t.getMatches())
			{
				
				if (/*worstTile1 == null || */ pm.getDistance() > worstDistance)
				{
					worstDistance = pm.getDistance();
					
					
					worstTile1 = t;
					worstTile2 = t.findConnectedTile( pm );
				}
				
				//System.out.println( pm.getDistance() + " " + worstDistance + " " + worstTile1 );
			}
		}

		if (worstTile1 == null)
		{
			System.err.println( "WARNING: can not remove any more links without disconnecting components" );
			return false;
		}
		
		worstTile1.removeConnectedTile( worstTile2 );
		worstTile2.removeConnectedTile( worstTile1 );	
		System.out.println( "removed link from " + worstTile1 + " to " + worstTile2 );
		return true;
	}

}
