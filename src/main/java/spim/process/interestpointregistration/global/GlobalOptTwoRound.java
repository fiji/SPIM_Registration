package spim.process.interestpointregistration.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.models.Affine3D;
import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.linkremoval.LinkRemovalStrategy;
import spim.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.weak.WeakLinkFactory;
import spim.process.interestpointregistration.global.pointmatchcreating.weak.WeakLinkPointMatchCreator;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class GlobalOptTwoRound
{
	/**
	 * 
	 * @param model - the transformation model to run the global optimizations on
	 * @param pmc - the pointmatch creator (makes mpicbg PointMatches from anything,
	 * e.g. corresponding interest points or stitching results)
	 * @param csStrong - the Iterative Convergence strategy applied to the strong links,
	 * as created by the pmc
	 * @param lms - decides for the iterative global optimization that is run on the
	 * strong links, which link to drop in an iteration
	 * @param wlf - a factory for creating weak links for the not optimized views.
	 * @param csWeak - the convergence strategy for optimizing the weak links, typically
	 * this is a new ConvergenceStrategy( Double.MAX_VALUE );
	 * @param fixedViews - which views are fixed
	 * @param groupsIn - which views are grouped
	 * @return
	 */
	public static < M extends Model< M > > HashMap< ViewId, AffineTransform3D > compute(
			final M model,
			final PointMatchCreator pmc,
			final IterativeConvergenceStrategy csStrong,
			final LinkRemovalStrategy lms,
			final WeakLinkFactory wlf,
			final ConvergenceStrategy csWeak,
			final Collection< ViewId > fixedViews,
			final Collection< Group< ViewId > > groupsIn )
	{
		// find strong links, run global opt iterative
		final HashMap< ViewId, Tile< M > > models = GlobalOptIterative.compute( model, pmc, csStrong, lms, fixedViews, groupsIn );

		// identify groups of connected views
		final List< Set< Tile< ? > > > sets = Tile.identifyConnectedGraphs( models.values() );

		// there is just one connected component -> all views already aligned
		// return first round results
		if (sets.size() == 1)
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Not more than one group left, we are already done." );

			final HashMap< ViewId, AffineTransform3D > finalRelativeModels = new HashMap<>();
			for ( final ViewId viewId : models.keySet() )
				finalRelativeModels.put( viewId, combineTransforms( models.get( viewId ), new AffineTransform3D() ) );

			return finalRelativeModels;
		}

		// every connected set becomes one group
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Identified the following (dis)connected groups:" );

		final ArrayList< Group< ViewId > > groupsNew = new ArrayList<>();
		for ( final Set< Tile< ? > > connected : sets )
		{
			final Group< ViewId > group = assembleViews( connected, models );
			groupsNew.add( group );

			IOFunctions.println( group );
		}

		// compute the weak links using the new groups and the results of the first run
		final WeakLinkPointMatchCreator< M > wlpmc = wlf.create( models );

		// run global opt without iterative
		final HashMap< ViewId, Tile< M > > models2 = GlobalOpt.compute( model, wlpmc, csWeak, fixedViews, groupsNew );

		// the models that were applied before running the second round
		final Map< ViewId, AffineGet > relativeTransforms = wlpmc.getRelativeTransforms();

		// the combination of models from:
		// the first round of global opt (strong links) + averageMapBack + the second round of global opt (weak links)
		final HashMap< ViewId, AffineTransform3D > finalRelativeModels = new HashMap<>();

		for ( final ViewId viewId : models2.keySet() )
			finalRelativeModels.put( viewId, combineTransforms( models2.get( viewId ), relativeTransforms.get( viewId ) ) );

		return finalRelativeModels;
	}

	public static < M extends Model< M > > AffineTransform3D combineTransforms( Tile< M > tile, final AffineGet relativeTransform )
	{
		final Affine3D< ? > tilemodel = (Affine3D< ? >)tile.getModel();
		final double[][] m = new double[ 3 ][ 4 ];
		tilemodel.toMatrix( m );
		
		final AffineTransform3D secondRunTransform = new AffineTransform3D();
		secondRunTransform.set(
				m[0][0], m[0][1], m[0][2], m[0][3],
				m[1][0], m[1][1], m[1][2], m[1][3],
				m[2][0], m[2][1], m[2][2], m[2][3] );

		// we concatenate, not pre-concatenate since the relativeTransform from the first round comes first
		secondRunTransform.concatenate( relativeTransform );

		return secondRunTransform;
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
