package spim.process.interestpointregistration.global.pointmatchcreating.weak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import spim.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
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

	/**
	 * @return - which transformation(s) have been applied before running the second round of global optimization
	 */
	public abstract Map< ViewId, AffineGet > getRelativeTransforms();
}
