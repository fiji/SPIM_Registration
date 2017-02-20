package spim.headless.registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.models.AffineModel3D;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.headless.interestpointdetection.DoGParameters;
import spim.headless.registration.geometrichashing.GeometricHashingParameters;
import spim.process.interestpointregistration.GlobalOpt;
import spim.process.interestpointregistration.pairwise.GeometricHashingPairwise;
import spim.process.interestpointregistration.pairwise.MatcherPairwiseTools;
import spim.process.interestpointregistration.pairwise.PairwiseResult;
import spim.process.interestpointregistration.pairwise.constellation.AllToAll;
import spim.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import spim.process.interestpointregistration.pairwise.constellation.Subset;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
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
			System.out.println( Group.pvid( viewId ) );

		testRegistration( spimData );
	}

	public static void testRegistration( final SpimData2 spimData )
	{
		// run DoG
		DoGParameters.testDoG( spimData );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		//
		// get interest point lists for "beads" and store a map ViewId >> InterestPointLabel
		//
		final String label = "beads"; // this could be different for each ViewId

		final Map< ViewId, InterestPointList > iplMap = new HashMap<>();
		final Map< ViewId, String > labelMap = new HashMap<>();

		for ( final ViewId viewId : viewIds )
		{
			iplMap.put( viewId, spimData.getViewInterestPoints().getViewInterestPoints().get( viewId ).getInterestPointList( label ) );
			labelMap.put( viewId, label );
		}

		// load & transform all interest points
		final Map< ViewId, List< InterestPoint > > interestpoints = TransformationTools.getAllTransformedInterestPoints(
				viewIds,
				spimData.getViewRegistrations().getViewRegistrations(),
				iplMap );

		// setup pairwise registration
		Set< Group< ViewId > > groups = new HashSet<>();
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
			// parameters
			final RANSACParameters rp = new RANSACParameters();
			final GeometricHashingParameters gp = new GeometricHashingParameters( new AffineModel3D() );

			// fix view(s)
			final List< ViewId > fixedViews = setup.getDefaultFixedViews();
			final ViewId fixedView = subset.getViews().iterator().next();
			fixedViews.add( fixedView );
			System.out.println( "Removed " + subset.fixViews( fixedViews ).size() + " views due to fixing view tpId=" + fixedView.getTimePointId() + " setupId=" + fixedView.getViewSetupId() );

			//
			// get all pairs to be compared (either that XOR grouped pairs)
			//
			final List< Pair< ViewId, ViewId > > pairs = subset.getPairs();

			for ( final Pair< ViewId, ViewId > pair : pairs )
				System.out.println( Group.pvid( pair.getA() ) + " <=> " + Group.pvid( pair.getB() ) );

			// compute all pairwise matchings
			final List< Pair< Pair< ViewId, ViewId >, PairwiseResult< InterestPoint > > > result =
					MatcherPairwiseTools.computePairs( pairs, interestpoints, new GeometricHashingPairwise< InterestPoint >( rp, gp ) );

			// clear correspondences
			MatcherPairwiseTools.clearCorrespondences( iplMap );

			// add the corresponding detections and output result
			for ( final Pair< Pair< ViewId, ViewId >, PairwiseResult< InterestPoint > > p : result )
			{
				final ViewId vA = p.getA().getA();
				final ViewId vB = p.getA().getB();

				final InterestPointList listA = iplMap.get( p.getA().getA() );
				final InterestPointList listB = iplMap.get( p.getA().getB() );

				MatcherPairwiseTools.addCorrespondences( p.getB().getInliers(), vA, vB, labelMap.get( vA ), labelMap.get( vB ), listA, listB );

				System.out.println( p.getB().getFullDesc() );
			}

			//
			// get all grouped pairs
			//
			final List< Pair< Group< ViewId >, Group< ViewId > > > groupedPairs = subset.getGroupedPairs();
			final Map< Group< ViewId >, List< GroupedInterestPoint< ViewId > > > groupedInterestpoints = new HashMap<>();
			final InterestPointGrouping< ViewId > ipGrouping = new InterestPointGroupingAll<>( interestpoints );

			// which groups exist
			groups.clear();

			for ( final Pair< Group< ViewId >, Group< ViewId > > pair : groupedPairs )
			{
				groups.add( pair.getA() );
				groups.add( pair.getB() );

				System.out.print( "[" + pair.getA() + "] <=> [" + pair.getB() + "]" );

				if ( !groupedInterestpoints.containsKey( pair.getA() ) )
				{
					System.out.print( ", grouping interestpoints for " + pair.getA() );

					groupedInterestpoints.put( pair.getA(), ipGrouping.group( pair.getA() ) );
				}

				if ( !groupedInterestpoints.containsKey( pair.getB() ) )
				{
					System.out.print( ", grouping interestpoints for " + pair.getB() );

					groupedInterestpoints.put( pair.getB(), ipGrouping.group( pair.getB() ) );
				}

				System.out.println();
			}

			final List< Pair< Pair< Group< ViewId >, Group< ViewId > >, PairwiseResult< GroupedInterestPoint< ViewId > > > > resultGroup =
					MatcherPairwiseTools.computePairs( groupedPairs, groupedInterestpoints, new GeometricHashingPairwise<>( rp, gp ) );

			// clear correspondences and get a map linking ViewIds to the correspondence lists
			final Map< ViewId, List< CorrespondingInterestPoints > > cMap = MatcherPairwiseTools.clearCorrespondences( iplMap );

			// add the corresponding detections and output result
			final List< Pair< Pair< ViewId, ViewId >, PairwiseResult< GroupedInterestPoint< ViewId > > > > resultG =
					MatcherPairwiseTools.addCorrespondencesFromGroups( resultGroup, iplMap, labelMap, cMap );

			// run global optimization
			final HashMap< ViewId, Tile< AffineModel3D > > models =
					GlobalOpt.compute( new AffineModel3D(), resultG, fixedViews, groups );

			/*

		// map-back model (useless as we fix the first one)
		final AffineTransform3D mapBack = computeMapBackModel(
				spimData.getSequenceDescription().getViewDescription( viewIds.get( 0 ) ).getViewSetup().getSize(),
				transformations.get( viewIds.get( 0 ) ).getModel(),
				models.get( viewIds.get( 0 ) ).getModel(),
				new RigidModel3D() );

		// pre-concatenate models to spimdata2 viewregistrations (from SpimData(2))
		for ( final ViewId viewId : viewIds )
		{
			final Tile< AffineModel3D > tile = models.get( viewId );
			final ViewRegistration vr = transformations.get( viewId );

			storeTransformation( vr, viewId, tile, mapBack, "AffineModel3D" );
		}

		// save XML?
		*/
		}
	}
}
