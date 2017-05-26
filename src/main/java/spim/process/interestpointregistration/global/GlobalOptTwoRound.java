package spim.process.interestpointregistration.global;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class GlobalOptTwoRound
{
	public static < M extends Model< M > > HashMap< ViewId, Tile< M > > compute(
			final M model,
			final PointMatchCreator pmc,
			final IterativeConvergenceStrategy ics,
			final ConvergenceStrategy cs,
			final Collection< ViewId > fixedViews,
			final Set< Group< ViewId > > groupsIn )
	{
		// find strong links, run global opt iterative

		// identify groups of connected views

		// make weak links for things that are not in the same group

		// run global opt without iterative

		return null;
	}
}
