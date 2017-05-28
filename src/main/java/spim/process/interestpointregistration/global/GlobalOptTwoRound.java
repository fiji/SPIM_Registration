package spim.process.interestpointregistration.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import spim.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.linkremoval.LinkRemovalStrategy;
import spim.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.WeakLinkFactory;
import spim.process.interestpointregistration.global.pointmatchcreating.WeakLinkPointMatchCreator;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class GlobalOptTwoRound
{
	public static < M extends Model< M > > HashMap< ViewId, Tile< M > > compute(
			final M model,
			final PointMatchCreator pmc,
			final IterativeConvergenceStrategy ics,
			final LinkRemovalStrategy lms,
			final WeakLinkFactory wlf,
			final ConvergenceStrategy cs,
			final Collection< ViewId > fixedViews,
			final Set< Group< ViewId > > groupsIn )
	{
		// merge overlapping groups if necessary
		final ArrayList< Group< ViewId > > groups = Group.mergeAllOverlappingGroups( groupsIn );

		// remove empty groups
		Group.removeEmptyGroups( groups );

		// find strong links, run global opt iterative
		final HashMap< ViewId, Tile< M > > models = GlobalOptIterative.compute( model, pmc, ics, lms, fixedViews, groups );

		// identify groups of connected views
		final List< Set< Tile< ? > > > sets = Tile.identifyConnectedGraphs( models.values() );

		// every connected set becomes one group
		final ArrayList< Group< ViewId > > groupsNew = new ArrayList<>();
		for ( final Set< Tile< ? > > connected : sets )
		{
			final Group< ViewId > group = assembleViews( connected, models );
			groupsNew.add( group );
		}

		// compute the weak links
		final WeakLinkPointMatchCreator< M > wlpmc = wlf.create( groupsNew, models );

		// run global opt without iterative
		final HashMap< ViewId, Tile< M > > models2 = GlobalOpt.compute( model, wlpmc, cs, fixedViews, groupsNew );

		// the models that were applied before running the second round
		final Map< ViewId, AffineGet > relativeTransforms = wlpmc.getRelativeTransforms();

		// TODO: combine models from 1st and 2nd round
		return models2;
	}

	public static Group< ViewId > assembleViews( final Set< Tile< ? > > set, final HashMap< ViewId, ? extends Tile< ? > > models )
	{
		final Group< ViewId > group = new Group<>();

		for ( final ViewId viewId : models.keySet() )
			if ( set.contains( models.get( viewId ) ) )
				group.getViews().add( viewId );

		return group;
	}
}
