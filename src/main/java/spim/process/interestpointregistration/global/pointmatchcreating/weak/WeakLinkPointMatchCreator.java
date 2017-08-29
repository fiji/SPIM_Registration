package spim.process.interestpointregistration.global.pointmatchcreating.weak;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import spim.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;

public abstract class WeakLinkPointMatchCreator< M extends Model< M > > implements PointMatchCreator
{
	final HashMap< ViewId, Tile< M > > models;
	final HashSet< ViewId > allViews;

	public WeakLinkPointMatchCreator( final HashMap< ViewId, Tile< M > > models )
	{
		this.models = models;
		this.allViews = new HashSet<>();

		this.allViews.addAll( models.keySet() );
	}

	/**
	 * @return - which transformation(s) have been applied before running the second round of global optimization
	 */
	public abstract Map< ViewId, AffineGet > getRelativeTransforms();

	@Override
	public HashSet< ViewId > getAllViews() { return allViews; }
}
