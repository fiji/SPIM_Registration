package spim.process.interestpointregistration.global;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.models.Affine3D;
import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.SimpleIterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.linkremoval.LinkRemovalStrategy;
import spim.process.interestpointregistration.global.linkremoval.MaxErrorLinkRemoval;
import spim.process.interestpointregistration.global.pointmatchcreating.ImageCorrelationPointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.MetaDataWeakLinkFactory;
import spim.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.WeakLinkFactory;
import spim.process.interestpointregistration.global.pointmatchcreating.WeakLinkPointMatchCreator;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class GlobalOptTwoRound
{
	public static < M extends Model< M > > HashMap< ViewId, AffineTransform3D > compute(
			final M model,
			final PointMatchCreator pmc,
			final IterativeConvergenceStrategy ics,
			final LinkRemovalStrategy lms,
			final WeakLinkFactory wlf,
			final ConvergenceStrategy cs,
			final Collection< ViewId > fixedViews,
			final Set< Group< ViewId > > groupsIn )
	{

		// find strong links, run global opt iterative
		final HashMap< ViewId, Tile< M > > models = GlobalOptIterative.compute( model, pmc, ics, lms, fixedViews, groupsIn );

		// identify groups of connected views
		final List< Set< Tile< ? > > > sets = Tile.identifyConnectedGraphs( models.values() );

		// there is just one connected component -> all views already aligned
		// return first round results
		if (sets.size() == 1)
		{
			final HashMap< ViewId, AffineTransform3D > finalRelativeModels = new HashMap<>();
			for ( final ViewId viewId : models.keySet() )
				finalRelativeModels.put( viewId, combineTransforms( models.get( viewId ), new AffineTransform3D() ) );

			return finalRelativeModels;
		}

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
	
	public static void main(String[] args)
	{
		final ViewId view0 = new ViewId( 0, 0 );
		final ViewId view1 = new ViewId( 0, 1 );
		final ViewId view2 = new ViewId( 0, 2 );

		final Group< ViewId > group0 = new Group<>( view0 );
		final Group< ViewId > group1 = new Group<>( view1 );
		final Group< ViewId > group2 = new Group<>( view2 );

		final HashSet< Group< ViewId > > groups = new HashSet<>();
		groups.add( group0 );
		groups.add( group1 );
		groups.add( group2 );

		final ArrayList< ViewId > fixed = new ArrayList<>();
		fixed.add( view0 );

		final BoundingBox bb = new BoundingBox( new int[]{ 0, 0, 0 }, new int[]{ 511, 511, 511 } );
		final ArrayList< PairwiseStitchingResult< ViewId > > pairwiseResults = new ArrayList<>();

		pairwiseResults.add( new PairwiseStitchingResult<>( new ValuePair<>( group0, group1 ), bb,  new Translation3D( 100, 0, 0 ), 0.5 ) );
		pairwiseResults.add( new PairwiseStitchingResult<>( new ValuePair<>( group1, group2 ), bb,  new Translation3D( 0, 100.25, 0 ), 0.1 ) );
		pairwiseResults.add( new PairwiseStitchingResult<>( new ValuePair<>( group0, group2 ), bb,  new Translation3D( 100, 100.5, 0 ), 0.1 ) );

		final IterativeConvergenceStrategy cs = new SimpleIterativeConvergenceStrategy( 10.0, 10.0, 10.0 );
		final PointMatchCreator pmc = new ImageCorrelationPointMatchCreator( pairwiseResults, 0.3 );
		
		final HashMap<ViewId, ViewRegistration> vrMap = new HashMap<>();
		
		AffineTransform3D tr0 = new AffineTransform3D();
		tr0.translate( new double[] {0.0, 0.0, 0.0} );
		tr0 = tr0.copy();
		new ViewRegistration( 0, 0, tr0 );
		vrMap.put( view0, new ViewRegistration( 0, 0, tr0 ) );
		
		AffineTransform3D tr1 = new AffineTransform3D();
		tr1.translate( new double[] {0.0, 300.0, 0.0} );
		tr1 = tr1.copy();
		new ViewRegistration( 0, 1, tr1 );
		vrMap.put( view1, new ViewRegistration( 0, 1, tr1 ) );
		
		AffineTransform3D tr2 = new AffineTransform3D();
		tr2.translate( new double[] {300.0, 300.0, 0.0} );
		tr2 = tr2.copy();
		new ViewRegistration( 0, 2, tr2 );
		vrMap.put( view2, new ViewRegistration( 0, 2, tr2 ) );
		
		ViewRegistrations vrs = new ViewRegistrations( vrMap );
		
		final HashMap< ViewId, AffineTransform3D > computeResults = compute( 
				new TranslationModel3D(),
				pmc,
				cs,
				new MaxErrorLinkRemoval(),
				new MetaDataWeakLinkFactory( vrs ),
				new ConvergenceStrategy( Double.MAX_VALUE ),
				fixed,
				groups );
		
		computeResults.forEach( ( k, v) -> {
			System.out.println( k + ": " + v );
		});
	}
}
