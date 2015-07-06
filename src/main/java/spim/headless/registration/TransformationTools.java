package spim.headless.registration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.models.Affine3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel3D;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.headless.interestpointdetection.DoGParameters;
import spim.headless.registration.geometrichashing.GeometricHashingParameters;
import spim.process.interestpointregistration.GlobalOpt;
import spim.process.interestpointregistration.pairwise.GeometricHashingPairwise;
import spim.process.interestpointregistration.pairwise.MatcherPairwiseTools;
import spim.process.interestpointregistration.pairwise.PairwiseStrategyTools;

public class TransformationTools
{
	/**
	 * 
	 * @param mapBackView - which view to use to map back
	 * @param mapBackViewRegistration - the registration of this view before computing the new registration
	 * @param computedModel - the new model
	 * @param mapBackModel - which model to use to map back (e.g. rigid, translation)
	 * @return the transformation to map back, pre-concatenate this to all views that are registered
	 */
	public static AffineTransform3D computeMapBackModel(
			final ViewDescription mapBackView,
			final ViewRegistration mapBackViewRegistration,
			final AffineModel3D computedModel,
			final Model< ? > mapBackModel )
	{
		if ( mapBackModel.getMinNumMatches() > 4 )
		{
			IOFunctions.println( "Cannot map back using a model that needs more than 4 points: " + mapBackModel.getClass().getSimpleName() );

			return null;
		}
		else
		{
			IOFunctions.println( "Mapping back to reference frame using a " + mapBackModel.getClass().getSimpleName() );

			final Dimensions size = mapBackView.getViewSetup().getSize(); //ViewSetupUtils.getSizeOrLoad( referenceTileSetup, mapBackView.getTimePoint(), imgLoader );
			long w = size.dimension( 0 );
			long h = size.dimension( 1 );

			// the top 4 corner points of the stack
			final double[][] p = new double[][]{
					{ 0, 0, 0 },
					{ w, 0, 0 },
					{ 0, h, 0 },
					{ w, h, 0 } };

			// original coordinates == pa (from mapBackViewRegistration)
			final double[][] pa = new double[ 4 ][ 3 ];

			// map coordinates to the actual input coordinates
			for ( int i = 0; i < p.length; ++i )
				mapBackViewRegistration.getModel().apply( p[ i ], pa[ i ] );

			// transformed coordinates == pb (from mapBackViewRegistration+computedModel)
			final double[][] pb = new double[ 4 ][ 3 ];

			for ( int i = 0; i < p.length; ++i )
				pb[ i ] = computedModel.apply( pa[ i ] );

			// compute the model that maps pb >> pa
			try
			{
				final ArrayList< PointMatch > pm = new ArrayList< PointMatch >();
				
				for ( int i = 0; i < p.length; ++i )
					pm.add( new PointMatch( new Point( pb[ i ] ), new Point( pa[ i ] ) ) );
				
				mapBackModel.fit( pm );
			} catch ( Exception e )
			{
				IOFunctions.println( "Could not compute model for mapping back: " + e );
				e.printStackTrace();
				return null;
			}

			final AffineTransform3D mapBack = new AffineTransform3D();
			final double[][] m = new double[ 3 ][ 4 ];
			((Affine3D<?>)mapBackModel).toMatrix( m );
			
			mapBack.set( m[0][0], m[0][1], m[0][2], + m[0][3],
						m[1][0], m[1][1], m[1][2], m[1][3], 
						m[2][0], m[2][1], m[2][2], m[2][3] );

			IOFunctions.println( "Model for mapping back: " + mapBack + "\n" );

			return mapBack;
		}
	}

	/** call this method to load interestpoints and apply current transformation */
	public static Map< ViewId, List< InterestPoint > > getAllTransformedInterestPoints(
			final Collection< ? extends ViewId > viewIds,
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

	public static void storeTransformation(
			final ViewRegistration vr,
			final ViewId viewId,
			final Tile< ? > tile,
			final AffineGet mapBackModel,
			final String modelDescription )
	{
		// TODO: we assume that M is an Affine3D, which is not necessarily true
		final Affine3D< ? > tilemodel = (Affine3D< ? >)tile.getModel();
		final double[][] m = new double[ 3 ][ 4 ];
		tilemodel.toMatrix( m );
		
		final AffineTransform3D t = new AffineTransform3D();
		t.set( m[0][0], m[0][1], m[0][2], m[0][3],
			   m[1][0], m[1][1], m[1][2], m[1][3],
			   m[2][0], m[2][1], m[2][2], m[2][3] );

		if ( mapBackModel != null )
			t.preConcatenate( mapBackModel );

		final ViewTransform vt = new ViewTransformAffine( modelDescription, t );
		vr.preconcatenateTransform( vt );
		vr.updateModel();
	}

	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		testRegistration(spimData);

	}

	// TODO: move into test package
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
		final Map< ViewId, List< InterestPoint > > interestpoints = getAllTransformedInterestPoints(
				viewIds,
				transformations,
				interestpointLists );

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

		for ( final Pair< Pair< ViewId, ViewId >, PairwiseResult > p : result )
			System.out.println( p.getA().getA().getViewSetupId() + "<>" + p.getA().getB().getViewSetupId()  + ": " + p.getB().result );

		final HashMap< ViewId, Tile< AffineModel3D > > models =
				GlobalOpt.compute( new AffineModel3D(), result, fixedViews, groupedViews );

		// map-back model (useless as we fix the first one)
		final AffineTransform3D mapBack = computeMapBackModel(
				spimData.getSequenceDescription().getViewDescription( viewIds.get( 0 ) ),
				transformations.get( viewIds.get( 0 ) ),
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
	}
}
