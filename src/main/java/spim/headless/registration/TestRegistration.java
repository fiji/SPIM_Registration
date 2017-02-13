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
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Grouping;
import spim.process.interestpointregistration.pairwise.constellation.grouping.InterestPointGrouping;
import spim.process.interestpointregistration.pairwise.constellation.grouping.InterestPointGroupingAll;
import spim.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;

public class TestRegistration
{
	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( pvid( viewId ) );

		testRegistration( spimData );
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
			final RANSACParameters rp = new RANSACParameters();
			final GeometricHashingParameters gp = new GeometricHashingParameters( new AffineModel3D() );

			final List< ViewId > fixedViews = setup.getDefaultFixedViews();
			final ViewId fixedView = subset.getViews().iterator().next();
			fixedViews.add( fixedView );

			System.out.println( "Removed " + subset.fixViews( fixedViews ).size() + " views due to fixing view tpId=" + fixedView.getTimePointId() + " setupId=" + fixedView.getViewSetupId() );

			// get all pairs
			final List< Pair< ViewId, ViewId > > pairs = subset.getPairs();

			for ( final Pair< ViewId, ViewId > pair : pairs )
				System.out.println( pvid( pair.getA() ) + " <=> " + pvid( pair.getB() ) );

			// compute all pairwise matchings
			final List< Pair< Pair< ViewId, ViewId >, PairwiseResult< InterestPoint > > > result =
					MatcherPairwiseTools.computePairs( pairs, interestpoints, new GeometricHashingPairwise< InterestPoint >( rp, gp ) );
			MatcherPairwiseTools.assignLoggingViewIdsAndDescriptions( result, spimData.getSequenceDescription() );

			// save the corresponding detections and output result
			for ( final Pair< Pair< ViewId, ViewId >, PairwiseResult< InterestPoint > > p : result )
			{
				final InterestPointList listA = spimData.getViewInterestPoints().getViewInterestPointLists( p.getA().getA() ).getInterestPointList( "beads" );
				final InterestPointList listB = spimData.getViewInterestPoints().getViewInterestPointLists( p.getA().getB() ).getInterestPointList( "beads" );
				TransformationTools.setCorrespondences( p.getB().getInliers(), p.getA().getA(), p.getA().getB(), "beads", "beads", listA, listB );

				System.out.println( p.getB().getFullDesc() );
			}

			// get all grouped pairs
			final List< Pair< Group< ViewId >, Group< ViewId > > > groupedPairs = subset.getGroupedPairs();
			final Map< Group< ViewId >, List< GroupedInterestPoint< ViewId > > > groupedInterestpoints = new HashMap<>();
			final InterestPointGrouping< ViewId > grouping = new InterestPointGroupingAll<>( interestpoints );

			for ( final Pair< Group< ViewId >, Group< ViewId > > pair : groupedPairs )
			{
				String groupA = "", groupB = "";

				for ( final ViewId a : pair.getA() )
					groupA += pvids( a ) + " ";

				for ( final ViewId b : pair.getB() )
					groupB += pvids( b ) + " ";

				System.out.print( "[ " + groupA + "] <=> [ " + groupB + "]" );

				if ( !groupedInterestpoints.containsKey( pair.getA() ) )
				{
					System.out.print( ", grouping interestpoints for " + groupA );

					final List< GroupedInterestPoint< ViewId > > groupedA = grouping.group( pair.getA() );
					groupedInterestpoints.put( pair.getA(), groupedA );
				}

				if ( !groupedInterestpoints.containsKey( pair.getB() ) )
				{
					System.out.print( ", grouping interestpoints for " + groupB );

					final List< GroupedInterestPoint< ViewId > > groupedB = grouping.group( pair.getB() );
					groupedInterestpoints.put( pair.getB(), groupedB );
				}

				System.out.println();
			}

			final List< Pair< Pair< Group< ViewId >, Group< ViewId > >, PairwiseResult< GroupedInterestPoint< ViewId > > > > resultGroup =
					MatcherPairwiseTools.computePairs( groupedPairs, groupedInterestpoints, new GeometricHashingPairwise< GroupedInterestPoint< ViewId > >( rp, gp ) );

			//MatcherPairwiseTools.assignGroupedLoggingViewIdsAndDescriptions( resultGroup, spimData.getSequenceDescription() );

			/*
			final HashMap< ViewId, Tile< AffineModel3D > > models =
					GlobalOpt.compute( new AffineModel3D(), result, fixedViews, groupedViews );

			// map-back model (useless as we fix the first one)
			final AffineTransform3D mapBack = computeMapBackModel(
					spimData.getSequenceDescription().getViewDescription( viewIds.get( 0 ) ).getViewSetup().getSize(),
					transformations.get( viewIds.get( 0 ) ).getModel(),
					models.get( viewIds.get( 0 ) ).getModel(),
					new RigidModel3D() ); */

		}
	}

	public static String pvid( final ViewId viewId ) { return "tpId=" + viewId.getTimePointId() + " setupId=" + viewId.getViewSetupId(); }
	public static String pvids( final ViewId viewId ) { return "t(" + viewId.getTimePointId() + ")-s(" + viewId.getViewSetupId() + ")"; }
	
}
