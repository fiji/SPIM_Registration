package spim.process.interestpointregistration.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import spim.process.interestpointregistration.pairwise.PairwiseResult;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class IterativeGlobalOpt
{
	public static < M extends Model< M > > HashMap< ViewId, Tile< M > > compute(
			final M model,
			final PointMatchCreator pmc,
			final IterativeConvergenceStrategy ics,
			final LinkRemovalStrategy lms,
			final Collection< ViewId > fixedViews,
			final Set< Group< ViewId > > groupsIn )
	{

		final Pair< HashMap< ViewId, Tile< M > >, ArrayList< Group< ViewId > > > globalOpt = GlobalOpt.initGlobalOpt( model, pmc, fixedViews, groupsIn );

		// assign ViewIds to the individual Tiles (either one tile per view or one tile per group)
		final HashMap< ViewId, Tile< M > > map = globalOpt.getA();

		// Groups are potentially modfied (merged, empty ones removed)
		final ArrayList< Group< ViewId > > groups = globalOpt.getB();

		// all views sorted (optional, but nice for user feedback)
		final ArrayList< ViewId > views = new ArrayList<>( map.keySet() );
		Collections.sort( views );

		// add and fix tiles as defined in the GlobalOptimizationType
		final TileConfiguration tc = GlobalOpt.addAndFixTiles( views, map, fixedViews, groups );

		// now perform the global optimization
		boolean finished = false;

		while (!finished)
		{
			try 
			{
				int unaligned = tc.preAlign().size();
				if ( unaligned > 0 )
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): pre-aligned all tiles but " + unaligned );
				else
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): prealigned all tiles" );

				tc.optimize( ics.getMaxError(), ics.getMaxIterations(), ics.getMaxPlateauWidth() );

				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Global optimization of " + 
						tc.getTiles().size());
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Avg Error: " + tc.getError() + "px" );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Min Error: " + tc.getMinError() + "px" );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Max Error: " + tc.getMaxError() + "px" );
			}
			catch (NotEnoughDataPointsException e)
			{
				IOFunctions.println( "Global optimization failed: " + e );
				e.printStackTrace();
			}
			catch (IllDefinedDataPointsException e)
			{
				IOFunctions.println( "Global optimization failed: " + e );
				e.printStackTrace();
			}

			finished = true;

			// re-do if errors are too big
			if ( !ics.isConverged( tc ) )
			{
				finished = false;

				// if we cannot remove any link, then we are finished too
				if ( !lms.removeLink( tc ) )
					finished = true;
			}
		}
		

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Transformation Models:" );

		return null;
	}
}
