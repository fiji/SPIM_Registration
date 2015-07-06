package spim.headless.registration;

import net.imglib2.realtransform.AffineTransform3D;

import mpicbg.models.AffineModel3D;
import mpicbg.models.RigidModel3D;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;

import spim.fiji.ImgLib2Temp;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;

import spim.process.interestpointregistration.GlobalOpt;
import spim.process.interestpointregistration.pairwise.GeometricHashingPairwise;
import spim.process.interestpointregistration.pairwise.MatcherPairwiseTools;
import spim.process.interestpointregistration.pairwise.PairwiseStrategyTools;

import spim.headless.interestpointdetection.DoGParameters;
import spim.headless.registration.geometrichashing.GeometricHashingParameters;
import simulation.imgloader.SimulatedBeadsImgLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import static spim.headless.registration.TransformationTools.computeMapBackModel;
import static spim.headless.registration.TransformationTools.getAllTransformedInterestPoints;
import static spim.headless.registration.TransformationTools.storeTransformation;

/**
 * GeometricHashing Test Class
 */
public class GeometricHashingTest
{
	@Test
	public void GeometricHashingWithDoGTest()
	{
		SpimData2 spimData = SpimData2.convert(
				SimulatedBeadsImgLoader.spimdataExample( new int[] { 0, 90, 135 } ) );

		// 1. Difference-of-Gaussian run
		DoGParameters.testDoG( spimData );

		// 2.1 Select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// 2.2 Collect corresponding current transformations
		final Map< ViewId, ViewRegistration > transformations = spimData.getViewRegistrations().getViewRegistrations();

		// 2.3 Get interest point lists for "beads"
		final Map< ViewId, ViewInterestPointLists > vipl = spimData.getViewInterestPoints().getViewInterestPoints();
		final Map< ViewId, InterestPointList > interestpointLists = new HashMap< ViewId, InterestPointList >();

		for ( final ViewId viewId : viewIds )
			interestpointLists.put( viewId, vipl.get( viewId ).getInterestPointList( "beads" ) );

		// 2.4 Load & transform all interest points
		final Map< ViewId, List< InterestPoint > > interestpoints = getAllTransformedInterestPoints(
				viewIds,
				transformations,
				interestpointLists );

		// 2.5 Define fixed tiles
		final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
		fixedViews.add( viewIds.get( 0 ) );

		// 2.6 Define groups
		final ArrayList< ArrayList< ViewId > > groupedViews = new ArrayList< ArrayList< ViewId > >();

		// 2.7 Define all pairs
		// This can be changed according to PairwiseStrategyTools
		final List< ImgLib2Temp.Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.allToAll( viewIds, fixedViews, groupedViews );

		// 2.8 Compute all pairwise matchings
		final RANSACParameters rp = new RANSACParameters();
		final GeometricHashingParameters gp = new GeometricHashingParameters( new AffineModel3D() );
		final List< ImgLib2Temp.Pair< ImgLib2Temp.Pair< ViewId, ViewId >, PairwiseResult > > result =
				MatcherPairwiseTools.computePairs( pairs, interestpoints, new GeometricHashingPairwise( rp, gp ) );

		// Print out all the pairwise results
		for ( final ImgLib2Temp.Pair< ImgLib2Temp.Pair< ViewId, ViewId >, PairwiseResult > p : result )
			System.out.println( p.getA().getA().getViewSetupId() + "<>" + p.getA().getB().getViewSetupId()  + ": " + p.getB().result );

		// 3.1 Compute global optimization
		final HashMap< ViewId, Tile< AffineModel3D > > models =
				GlobalOpt.compute( new AffineModel3D(), result, fixedViews, groupedViews );

		// 3.2 Apply map-back model (useless as we fix the first one)
		final AffineTransform3D mapBack = computeMapBackModel(
				spimData.getSequenceDescription().getViewDescription( viewIds.get( 0 ) ),
				transformations.get( viewIds.get( 0 ) ),
				models.get( viewIds.get( 0 ) ).getModel(),
				new RigidModel3D() );

		// 3.3 pre-concatenate models to spimdata2 viewregistrations (from SpimData(2))
		for ( final ViewId viewId : viewIds )
		{
			final Tile< AffineModel3D > tile = models.get( viewId );
			final ViewRegistration vr = transformations.get( viewId );

			storeTransformation( vr, viewId, tile, mapBack, "AffineModel3D" );
		}

		Assert.assertEquals( "MapBack", mapBack.toString(),
				"3d-affine: (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)" );
	}
}
