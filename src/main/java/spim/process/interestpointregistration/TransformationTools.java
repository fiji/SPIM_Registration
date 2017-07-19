package spim.process.interestpointregistration;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.AbstractModel;
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
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.vecmath.Matrix4f;
import spim.vecmath.Quat4f;
import spim.vecmath.Transform3D;
import spim.vecmath.Vector3d;
import spim.vecmath.Vector3f;

public class TransformationTools
{
	public static NumberFormat f = new DecimalFormat("#.####");

	/**
	 * WARNING: This fails on older MACs, in this case remove: 
	 * 
	 * Check if Apple's out-dated Java 3D version 1.3 is installed in System/Library/Java/Extensions/ on your Mac. 
	 * Remove all Java 3D 1.3 related files including vecmath.jar (jar, jnilib), they are useless.
	 * 
	 * @param model - the model
	 * @return - String description of rot. axis
	 */
	public static String getRotationAxis( final RigidModel3D model )
	{
		try
		{
			final Matrix4f matrix = new Matrix4f();
			getTransform3D( model ).get( matrix );
			final Quat4f qu = new Quat4f();
			qu.set( matrix );
			
			final Vector3f n = new Vector3f(qu.getX(),qu.getY(), qu.getZ());
			n.normalize();
			
			return "Approx. axis: " + n + ", approx. angle: " + Math.toDegrees( Math.acos( qu.getW() ) * 2 );
		}
		catch ( Exception e )
		{
			return "Check if Apple's out-dated Java 3D version 1.3 is installed in System/Library/Java/Extensions/ on your Mac." +
					"Remove all Java 3D 1.3 related files including vecmath.jar (jar, jnilib), they are useless.";
		}
	}

	public static void getScaling( final Affine3D< ? > affine, final double[] scale ) { getScaling( getTransform3D( affine ), scale ); }
	public static void getScaling( final AffineGet affine, final double[] scale ) { getScaling( getTransform3D( affine ), scale ); }

	public static String getScaling( final Affine3D< ? > affine ) { return getScaling( getTransform3D( affine ) ); }

	public static String getScaling( final AffineGet affine ) { return getScaling( getTransform3D( affine ) ); }

	public static String getScaling( final Transform3D t )
	{
		final Vector3d v = new Vector3d();
		t.getScale( v );
		return "Scaling: " + f.format( v.x ) + ", " + f.format( v.y ) + ", " + f.format( v.z );
	}

	public static void getScaling( final Transform3D t, double[] scale )
	{
		final Vector3d v = new Vector3d();
		t.getScale( v );
		scale[ 0 ] = v.x;
		scale[ 1 ] = v.y;
		scale[ 2 ] = v.z;
	}

	public static Transform3D getTransform3D( final Affine3D< ? > affine )
	{
		final double[][] m = new double[ 3 ][ 4 ];
		((Affine3D<?>)affine).toMatrix( m );

		final Transform3D transform = new Transform3D();
		final double[] m2 = new double[ 16 ];
		transform.get( m2 );
		
		m2[ 0 ] = m[0][0];
		m2[ 1 ] = m[0][1];
		m2[ 2 ] = m[0][2];
		m2[ 3 ] = m[0][3];

		m2[ 4 ] = m[1][0];
		m2[ 5 ] = m[1][1];
		m2[ 6 ] = m[1][2];
		m2[ 7 ] = m[1][3];

		m2[ 8 ] = m[2][0];
		m2[ 9 ] = m[2][1];
		m2[ 10] = m[2][2];
		m2[ 11] = m[2][3];

		transform.set( m2 );

		return transform;
	}

	public static Transform3D getTransform3D( final AffineGet affine )
	{
		final double[] m = affine.getRowPackedCopy();

		final Transform3D transform = new Transform3D();
		final double[] m2 = new double[ 16 ];
		transform.get( m2 );
		
		m2[ 0 ] = m[ 0 ];
		m2[ 1 ] = m[ 1 ];
		m2[ 2 ] = m[ 2 ];
		m2[ 3 ] = m[ 3 ];

		m2[ 4 ] = m[ 4 ];
		m2[ 5 ] = m[ 5 ];
		m2[ 6 ] = m[ 6 ];
		m2[ 7 ] = m[ 7 ];

		m2[ 8 ] = m[ 8 ];
		m2[ 9 ] = m[ 9 ];
		m2[ 10] = m[ 10];
		m2[ 11] = m[ 11];

		transform.set( m2 );

		return transform;
	}

	public static <M extends AbstractAffineModel3D<M>> Transform3D getTransform3D( final M model )
	{
		final Transform3D transform = new Transform3D();
		final double[] m = model.getMatrix( null );

		final double[] m2 = new double[ 16 ];
		transform.get( m2 );

		for ( int i = 0; i < m.length; ++i )
			m2[ i ] = m[ i ];

		transform.set( m2 );

		return transform;
	}

	public static String printAffine3D( final Affine3D< ? > model )
	{
		final double[][] m = new double[ 3 ][ 4 ];
		model.toMatrix( m );
		
		return
			"(" + f.format( m[0][0] ) + ", " + f.format( m[0][1] ) + ", " + f.format( m[0][2] ) + ", " + f.format( m[0][3] ) + ")," +
			"(" + f.format( m[1][0] ) + ", " + f.format( m[1][1] ) + ", " + f.format( m[1][2] ) + ", " + f.format( m[1][3] ) + ")," +
			"(" + f.format( m[2][0] ) + ", " + f.format( m[2][1] ) + ", " + f.format( m[2][2] ) + ", " + f.format( m[2][3] ) + ")";
	}

	public static String printAffine3D( final AffineGet model )
	{
		final double[] m = model.getRowPackedCopy();

		return
			"(" + f.format( m[ 0 ] ) + ", " + f.format( m[ 1 ] ) + ", " + f.format( m[ 2 ] ) + ", " + f.format( m[ 3 ] ) + ")," +
			"(" + f.format( m[ 4 ] ) + ", " + f.format( m[ 5 ] ) + ", " + f.format( m[ 6 ] ) + ", " + f.format( m[ 7 ] ) + ")," +
			"(" + f.format( m[ 8 ] ) + ", " + f.format( m[ 9 ] ) + ", " + f.format( m[ 10 ] )+ ", " + f.format( m[ 11 ] )+ ")";
	}

	public static AffineModel3D getModel( final AffineGet affine )
	{
		final double[] m = affine.getRowPackedCopy();
		final AffineModel3D model = new AffineModel3D();
		model.set( m[ 0 ], m[ 1 ], m[ 2 ], m[ 3 ], m[ 4 ], m[ 5 ], m[ 6 ], m[ 7 ], m[ 8 ], m[ 9 ], m[ 10 ], m[ 11 ] );
		return model;
	}

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

			return mapBack;
		}
	}

	/* call this method to load interestpoints and apply current transformation */
	public static <V> Map< V, List< InterestPoint > > getAllTransformedInterestPoints(
			final Collection< ? extends V > viewIds,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap )
	{
		return getAllInterestPoints( viewIds, registrations, interestpoints, labelMap, true );
	}

	/* call this method to load interestpoints and apply current transformation */
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

	/* call this method to load interestpoints and apply current transformation */
	public static <V> List< InterestPoint > getTransformedInterestPoints(
			final V viewId,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap )
	{
		return getInterestPoints( viewId, registrations, interestpoints, labelMap, true );
	}

	/* call this method to load interestpoints and apply current transformation if necessary */
	public static <V> List< InterestPoint > getInterestPoints(
			final V viewId,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap,
			final boolean transform )
	{
		final List< InterestPoint > list = loadInterestPoints( interestpoints.get( viewId ).getInterestPointList( labelMap.get( viewId ) ) );

		if ( list == null )
		{
			if ( ViewId.class.isInstance( viewId  ))
				IOFunctions.println( "WARNING: no interestpoints could be loaded for " + Group.pvid( (ViewId)viewId ) + ", label '" + labelMap.get( viewId ) + "'" );
			else
				IOFunctions.println( "WARNING: no interestpoints could be loaded for " + viewId + ", label '" + labelMap.get( viewId ) + "'" );

			return new ArrayList<>();
		}
		else if ( transform )
		{
			final AffineTransform3D t = getTransform( viewId, registrations );
			return applyTransformation( list, t );
		}
		else
		{
			return list;
		}
	}

	/* call this method to load interestpoints and apply current transformation */
	public static <V> List< InterestPoint > getTransformedCorrespondingInterestPoints(
			final V viewId,
			final Map< V, ViewRegistration > registrations,
			final Map< V, ViewInterestPointLists > interestpoints,
			final Map< V, String > labelMap )
	{
		return getCorrespondingInterestPoints( viewId, registrations, interestpoints, labelMap, true );
	}

	/* call this method to load interestpoints and apply current transformation */
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

		if ( allPoints == null )
		{
			if ( ViewId.class.isInstance( viewId  ))
				IOFunctions.println( "WARNING: no interestpoints could be loaded for " + Group.pvid( (ViewId)viewId ) + ", label '" + labelMap.get( viewId ) + "'" );
			else
				IOFunctions.println( "WARNING: no interestpoints could be loaded for " + viewId + ", label '" + labelMap.get( viewId ) + "'" );

			return new ArrayList<>();
		}

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
		if ( list == null )
			return null;

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
