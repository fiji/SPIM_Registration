package spim.process.interestpointregistration.global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public interface PointMatchCreator
{
	/**
	 * @return - all views that this class knows and that are part of the global opt
	 */
	public HashSet< ViewId > getAllViews();

	/**
	 * assign pointmatches for all views that this object knows and that are present in tileMap.keySet(), which comes from getAllViews() plus what is in the group definition of the globalopt
	 * 
	 * @param tileMap - the map from viewId to Tile
	 */
	public < M extends Model< M > > void assignPointMatches( final HashMap< ViewId, Tile< M > > tileMap );

	/**
	 * By default all weights are 1, if wanted one can adjust them here, otherwise simply return
	 * 
	 * @param groups - which groups exist
	 * @param tileMap - the map from viewId to Tile
	 */
	public < M extends Model< M > > void assignWeights( final ArrayList< Group< ViewId > > groups, final HashMap< ViewId, Tile< M > > tileMap );
}
