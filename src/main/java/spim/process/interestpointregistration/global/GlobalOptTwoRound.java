package spim.process.interestpointregistration.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.linkremoval.LinkRemovalStrategy;
import spim.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class GlobalOptTwoRound
{
	public static < M extends Model< M > > HashMap< ViewId, Tile< M > > compute(
			final M model,
			final PointMatchCreator pmc,
			final IterativeConvergenceStrategy ics,
			final LinkRemovalStrategy lms,
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

		// make weak links for things that are not in the same group

		// run global opt without iterative

		return null;
	}
}
