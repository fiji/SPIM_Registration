package spim.headless.registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.models.AffineModel3D;
import mpicbg.models.RigidModel3D;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.headless.interestpointdetection.DoGParameters;
import spim.headless.registration.geometrichashing.GeometricHashingParameters;
import spim.process.interestpointregistration.GlobalOpt;
import spim.process.interestpointregistration.pairwise.GeometricHashingPairwise;
import spim.process.interestpointregistration.pairwise.MatcherPairwiseTools;
import spim.process.interestpointregistration.pairwise.PairwiseResult;
import spim.process.interestpointregistration.pairwise.PairwiseStrategyTools;
import spim.process.interestpointregistration.pairwise.constellation.AllToAll;
import spim.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import spim.process.interestpointregistration.pairwise.constellation.Subset;
import spim.process.interestpointregistration.pairwise.constellation.group.Group;
import spim.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;

public class TestRegistration
{
	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		testRegistration(spimData );
	}

	public static void testRegistration( final SpimData2 spimData )
	{
		// run DoG
		DoGParameters.testDoG( spimData );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// collect corresponding current transformations
		final Map< ViewId, ViewRegistration > transformations = spimData.getViewRegistrations().getViewRegistrations();

		// get interest point lists for "beads"
		final Map< ViewId, ViewInterestPointLists > vipl = spimData.getViewInterestPoints().getViewInterestPoints();
		final Map< ViewId, InterestPointList > interestpointLists = new HashMap< ViewId, InterestPointList >();

		for ( final ViewId viewId : viewIds )
			interestpointLists.put( viewId, vipl.get( viewId ).getInterestPointList( "beads" ) );

		// load & transform all interest points
		final Map< ViewId, List< InterestPoint > > interestpoints = TransformationTools.getAllTransformedInterestPoints(
				viewIds,
				transformations,
				interestpointLists );

		// setup pairwise registration
		final Set< Group< ViewId > > groups = new HashSet<>();
		final PairwiseSetup< ViewId > setup = new AllToAll<>( viewIds, groups );

		System.out.println( "Defined pairs, removed " + setup.definePairs().size() + " redundant view pairs." );
		System.out.println( "Removed " + setup.removeNonOverlappingPairs( new SimpleBoundingBoxOverlap<>( spimData ) ).size() + " pairs because they do not overlap." );
		setup.reorderPairs();
		setup.detectSubsets();
		setup.sortSubsets();
		final ArrayList< Subset< ViewId > > subsets = setup.getSubsets();
		System.out.println( "Identified " + subsets.size() + " subsets " );

		for ( final Subset< ViewId > subset : subsets )
		{
			final List< ViewId > fixedViews = setup.getDefaultFixedViews();
			final ViewId fixedView = subset.getViews().iterator().next();
			fixedViews.add( fixedView );

			System.out.println( "Removed " + subset.fixViews( fixedViews ).size() + " views due to fixing view " + fixedView );
		}
		
		System.exit( 0 );

		// 
		// subset.fixViews() - fixed some of the views necessary for the strategy to work

		// define fixed tiles
		final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
		fixedViews.add( viewIds.get( 0 ) );

		// define groups
		final ArrayList< ArrayList< ViewId > > groupedViews = new ArrayList< ArrayList< ViewId > >();

		// define all pairs
		final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.allToAll( viewIds, fixedViews, groupedViews );

		// compute all pairwise matchings
		final RANSACParameters rp = new RANSACParameters();
		final GeometricHashingParameters gp = new GeometricHashingParameters( new AffineModel3D() );
		final List< Pair< Pair< ViewId, ViewId >, PairwiseResult > > result =
				MatcherPairwiseTools.computePairs( pairs, interestpoints, new GeometricHashingPairwise( rp, gp ) );
		MatcherPairwiseTools.assignLoggingViewIdsAndDescriptions( result, spimData.getSequenceDescription() );

	}

}
