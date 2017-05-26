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
	public HashSet< ViewId > getAllViews();
	public < M extends Model< M > > void assignPointMatches( final HashMap< ViewId, Tile< M > > map );
	public < M extends Model< M > > void assignWeights( final ArrayList< Group< ViewId > > groups, final HashMap< ViewId, Tile< M > > tileMap );
}
