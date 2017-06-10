package spim.process.interestpointregistration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import mpicbg.models.AbstractModel;
import mpicbg.models.Affine3D;
import mpicbg.models.Model;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;

public class TransformationTools
{
	/**
	 * 
	 * @param size - size of view which is used to map back
	 * @param mapBackViewRegistration - the registration model of this view before computing the new registration
	 * @param computedModel - the new model
	 * @param mapBackModel - which model to use to map back (e.g. rigid, translation)
	 * @return the transformation to map back, pre-concatenate this to all views that are registered
	 */
	public static AffineTransform3D computeMapBackModel(
			final Dimensions size,
			final AffineTransform3D mapBackViewRegistration,
			final AbstractModel< ? > computedModel,
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
				mapBackViewRegistration.apply( p[ i ], pa[ i ] );

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
	public static <V> Map< V, List< InterestPoint > > getAllTransformedInterestPoints(
			final Collection< ? extends V > viewIds,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap )
	{
		return getAllInterestPoints( viewIds, registrations, interestpoints, labelMap, true );
	}

	/** call this method to load interestpoints and apply current transformation */
	public static <V> Map< V, List< InterestPoint > > getAllInterestPoints(
			final Collection< ? extends V > viewIds,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap,
			final boolean transform )
	{
		final HashMap< V, List< InterestPoint > > transformedInterestpoints =
				new HashMap< V, List< InterestPoint > >();

		for ( final V viewId : viewIds )
			transformedInterestpoints.put( viewId, getInterestPoints( viewId, registrations, interestpoints, labelMap, transform ) );

		return transformedInterestpoints;
	}

	/** call this method to load interestpoints and apply current transformation */
	public static <V> List< InterestPoint > getTransformedInterestPoints(
			final V viewId,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap )
	{
		return getInterestPoints( viewId, registrations, interestpoints, labelMap, true );
	}

	/** call this method to load interestpoints and apply current transformation if necessary */
	public static <V> List< InterestPoint > getInterestPoints(
			final V viewId,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap,
			final boolean transform )
	{
		final List< InterestPoint > list = loadInterestPoints( interestpoints.get( viewId ).getInterestPointList( labelMap.get( viewId ) ) );

		if ( transform )
		{
			final AffineTransform3D t = getTransform( viewId, registrations );
			return applyTransformation( list, t );
		}
		else
		{
			return list;
		}
	}

	/** call this method to load interestpoints and apply current transformation */
	public static <V> List< InterestPoint > getTransformedCorrespondingInterestPoints(
			final V viewId,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap )
	{
		return getCorrespondingInterestPoints( viewId, registrations, interestpoints, labelMap, true );
	}

	/** call this method to load interestpoints and apply current transformation */
	public static <V> List< InterestPoint > getCorrespondingInterestPoints(
			final V viewId,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap,
			final boolean transform )
	{
		final InterestPointList ipList = interestpoints.get( viewId ).getInterestPointList( labelMap.get( viewId ) );
		final List< InterestPoint > allPoints = loadInterestPoints( ipList );
		final ArrayList< InterestPoint > corrPoints = new ArrayList<>();
		
		// keep only those interest points who have correspondences
		final HashSet< Integer > idSet = new HashSet<>();

		for ( final CorrespondingInterestPoints cip : loadCorrespondingInterestPoints( ipList ) )
			idSet.add( cip.getDetectionId() );

		for ( final InterestPoint ip : allPoints )
			if ( idSet.contains( ip.getId() ) )
				corrPoints.add( ip );

		if ( transform )
		{
			final AffineTransform3D t = getTransform( viewId, registrations );
			return applyTransformation( corrPoints, t );
		}
		else
		{
			return corrPoints;
		}
	}

	public static List< InterestPoint > loadInterestPoints( final InterestPointList list )
	{
		if ( !list.hasInterestPoints() )
			list.loadInterestPoints();

		return list.getInterestPointsCopy();
	}

	public static < M extends Model< M > > AffineTransform3D averageTransform(
			final Collection< AffineTransform3D > models )
	{
		final double[] sum = new double[ 12 ];

		for ( final AffineTransform3D t : models )
		{
			System.out.println( t );
			final double[] tmp = t.getRowPackedCopy();

			for ( int i = 0; i < sum.length; ++i )
				sum[ i ] += tmp[ i ];
		}

		for ( int i = 0; i < sum.length; ++i )
			sum[ i ] /= (double)models.size();

		final AffineTransform3D affine = new AffineTransform3D();

		affine.set(
				sum[ 0 ], sum[ 1 ], sum[ 2 ], sum[ 3 ],
				sum[ 4 ], sum[ 5 ], sum[ 6 ], sum[ 7 ],
				sum[ 8 ], sum[ 9 ], sum[ 10 ], sum[ 11 ] );

		System.out.println( affine );

		return affine;
	}

	public static List< CorrespondingInterestPoints > loadCorrespondingInterestPoints( final InterestPointList list )
	{
		if ( !list.hasCorrespondingInterestPoints() )
			list.loadCorrespondingInterestPoints();

		return list.getCorrespondingInterestPointsCopy();
	}

	public static <V> AffineTransform3D getTransform( final V viewId, final Map< V, ViewRegistration > registrations )
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

	public static <V> void storeTransformation(
			final ViewRegistration vr,
			final V viewId,
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
}
