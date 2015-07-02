package spim.process.interestpointregistration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.models.AffineModel3D;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.headless.interestpointdetection.DoGParameters;
import spim.headless.registration.PairwiseResult;
import spim.headless.registration.RANSACParameters;
import spim.headless.registration.geometrichashing.GeometricHashingParameters;
import spim.process.interestpointregistration.pairwise.GeometricHashingPairwise;
import spim.process.interestpointregistration.pairwise.MatcherPairwiseTools;
import spim.process.interestpointregistration.pairwise.PairwiseStrategyTools;

public class TransformationTools
{
	/** call this method to load interestpoints and apply current transformation */
	public static Map< ViewId, List< InterestPoint > > getAllTransformedInterestPoints(
			final Collection< ViewId > viewIds,
			final Map< ViewId, ViewRegistration > registrations,
			final Map< ViewId, InterestPointList > interestpoints )
	{
		final HashMap< ViewId, List< InterestPoint > > transformedInterestpoints =
				new HashMap< ViewId, List< InterestPoint > >();

		for ( final ViewId viewId : viewIds )
			transformedInterestpoints.put( viewId, getTransformedInterestPoints( viewId, registrations, interestpoints ) );

		return transformedInterestpoints;
	}

	/** call this method to load interestpoints and apply current transformation */
	public static List< InterestPoint > getTransformedInterestPoints(
			final ViewId viewId,
			final Map< ViewId, ViewRegistration > registrations,
			final Map< ViewId, InterestPointList > interestpoints )
	{
		final List< InterestPoint > list = loadInterestPoints( interestpoints.get( viewId ) );
		final AffineTransform3D t = getTransform( viewId, registrations );

		return applyTransformation( list, t );
	}

	public static List< InterestPoint > loadInterestPoints( final InterestPointList list )
	{
		if ( list.getInterestPoints() == null )
			list.loadInterestPoints();

		return list.getInterestPoints();
	}

	public static AffineTransform3D getTransform( final ViewId viewId, final Map< ViewId, ViewRegistration > registrations )
	{
		final ViewRegistration r = registrations.get( viewId );
		r.updateModel();
		return r.getModel();
	}

	public static List< InterestPoint > applyTransformation( final List< InterestPoint > list, final AffineTransform3D m )
	{
		final ArrayList< InterestPoint > transformedList = new ArrayList< InterestPoint >();

		for ( final InterestPoint p : list )
		{
			final double[] l = new double[ 3 ];
			m.apply( p.getL(), l );
			
			transformedList.add( new InterestPoint( p.getId(), l ) );
		}

		return transformedList;
	}

	public static SpimData2 convert( final SpimData data1 )
	{
		final SequenceDescription s = data1.getSequenceDescription();
		final ViewRegistrations vr = data1.getViewRegistrations();
		final ViewInterestPoints vipl = new ViewInterestPoints();
		final BoundingBoxes bb = new BoundingBoxes();

		return new SpimData2( data1.getBasePath(), s, vr, vipl, bb );
	}

	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90 } ) );

		testRegistration(spimData);

	}

	// TODO: move into test package
	private static void testRegistration(SpimData2 spimData)
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
		final Map< ViewId, List< InterestPoint > > interestpoints = getAllTransformedInterestPoints(
				viewIds,
				transformations,
				interestpointLists );

		// define fixed tiles
		final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
		fixedViews.add( viewIds.get( 0 ) );

		// define groups
		final ArrayList< ArrayList< ViewId > > groupedViews = new ArrayList<ArrayList<ViewId>>();

		// define all pairs
		final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.allToAll( viewIds, fixedViews, groupedViews );

		// compute all pairwise matchings
		final RANSACParameters rp = new RANSACParameters();
		final GeometricHashingParameters gp = new GeometricHashingParameters( new AffineModel3D() );
		final List< Pair< Pair< ViewId, ViewId >, PairwiseResult > > result =
				MatcherPairwiseTools.computePairs( pairs, interestpoints, new GeometricHashingPairwise( rp, gp ) );

		for ( final Pair< Pair< ViewId, ViewId >, PairwiseResult > p : result )
			System.out.println( p.getA().getA().getViewSetupId() + "<>" + p.getA().getB().getViewSetupId()  + ": " + p.getB().result );
	}
}
