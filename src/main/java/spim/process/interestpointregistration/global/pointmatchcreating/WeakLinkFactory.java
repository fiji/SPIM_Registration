package spim.process.interestpointregistration.global.pointmatchcreating;

import java.util.ArrayList;
import java.util.HashMap;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public interface WeakLinkFactory
{
	public < M extends Model< M > > WeakLinkPointMatchCreator< M > create( final ArrayList< Group< ViewId > > groupsNew, final HashMap< ViewId, Tile< M > > models );
}
