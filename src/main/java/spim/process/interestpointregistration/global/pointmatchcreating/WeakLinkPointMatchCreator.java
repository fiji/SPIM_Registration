package spim.process.interestpointregistration.global.pointmatchcreating;

import java.util.ArrayList;
import java.util.HashMap;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public abstract class WeakLinkPointMatchCreator< M extends Model< M > > implements PointMatchCreator
{
	final ArrayList< Group< ViewId > > groupsNew;
	final HashMap< ViewId, Tile< M > > models;

	public WeakLinkPointMatchCreator( final ArrayList< Group< ViewId > > groupsNew, final HashMap< ViewId, Tile< M > > models )
	{
		this.groupsNew = groupsNew;
		this.models = models;
	}
}
