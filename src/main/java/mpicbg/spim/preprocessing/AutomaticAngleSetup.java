package mpicbg.spim.preprocessing;

import java.util.ArrayList;

import spim.vecmath.Transform3D;
import spim.vecmath.AxisAngle4d;
import spim.vecmath.Matrix3d;
import spim.vecmath.Point3d;
import spim.vecmath.Vector3d;

import mpicbg.models.AffineModel3D;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.io.SPIMConfiguration;
import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.ViewStructure;
import mpicbg.util.TransformUtils;

public class AutomaticAngleSetup 
{
	public AutomaticAngleSetup( final ViewStructure viewStructure )
	{		
		final ArrayList<ViewDataBeads>views = viewStructure.getViews();
		
		final ViewDataBeads viewA = views.get( 0 ); 
		final ViewDataBeads viewB = views.get( 1 );
		
		IOFunctions.println("Using view " + viewA.getName() + " and " + viewB.getName() + " for automatic angle setup." );
		
		final Vector3d rotationAxis = extractRotationAxis( (AffineModel3D)viewA.getTile().getModel(), (AffineModel3D)viewB.getTile().getModel() );
		//final float rotationAngle = extractRotationAngle( viewA.getTile().getModel(), viewB.getTile().getModel(), rotationAxis );
		//IOFunctions.println( "rotation axis: " + rotationAxis + ", angle: " + rotationAngle );
				
		IOFunctions.println( "rotation axis: " + rotationAxis );		
		rotationAxis.normalize();		
		IOFunctions.println( "rotation axis normed: " + rotationAxis );
		final Vector3d xAxis = new Vector3d( new double[] { 1, 0, 0 } );
				
		IOFunctions.println( "Difference to xAxis: " + distance( rotationAxis, xAxis ) );
		//testRotationAxis();
		//getCommonAreaPerpendicularViews( viewA, viewB );
	}
	
	public static double distance( final Vector3d a, final Vector3d b )
	{
		final double dx = a.x - b.x;
		final double dy = a.y - b.y;
		final double dz = a.z - b.z;
		
		return Math.sqrt( dx*dx + dy*dy + dz*dz );
	}
	
	public static void testRotationAxis()
	{
		Transform3D a = new Transform3D();
		Transform3D b = new Transform3D();

		a.setRotation( new AxisAngle4d( new Vector3d( 1, 2, 3), Math.toRadians( 45 ) ) );
		b.setRotation( new AxisAngle4d( new Vector3d( 1, 2, 3), Math.toRadians( 90 ) ) );
		
		Transform3D c = new Transform3D( a );
		
		c.mul( a );
		
		IOFunctions.println(c);
		IOFunctions.println(b);
				
		Vector3d roationAxis = extractRotationAxis ( TransformUtils.getAffineModel3D( a ), TransformUtils.getAffineModel3D( b )  );

		IOFunctions.println( roationAxis );				
		IOFunctions.println( extractRotationAngle( TransformUtils.getAffineModel3D( a ), TransformUtils.getAffineModel3D( b ), roationAxis) );		
		
	}

	public static double extractRotationAngle( final AffineModel3D modelA, final AffineModel3D modelB, final Vector3d rotationAxis )
	{
		final Transform3D transformA = TransformUtils.getTransform3D( modelA );
		final Transform3D transformB = TransformUtils.getTransform3D( modelB );

		// reset translational components
		transformA.setTranslation( new Vector3d() );
		transformB.setTranslation( new Vector3d() );
		
		final Transform3D connectingTransform = new Transform3D();
		final Transform3D tmp = new Transform3D();
		
		final Matrix3d matrix1 = new Matrix3d();
		final Matrix3d matrix2 = new Matrix3d();
		
		transformB.get( matrix2 );
		
		double minError = Float.MAX_VALUE;
		double minAngle = -1;
		
		for ( double angle = 0f; angle < 360.0f; angle += 1f )
		{
			connectingTransform.setIdentity();
			connectingTransform.setRotation( new AxisAngle4d( rotationAxis, Math.toRadians( angle ) ) );
		
			tmp.set( transformA );
			tmp.mul( connectingTransform );
			
			tmp.get( matrix1 );
			
			matrix1.sub( matrix2 );
			
			final double diff = matrix1.m00 * matrix1.m00 + matrix1.m01 * matrix1.m01 + matrix1.m02 * matrix1.m02 +
							   matrix1.m10 * matrix1.m10 + matrix1.m11 * matrix1.m11 + matrix1.m12 * matrix1.m12 +
							   matrix1.m20 * matrix1.m20 + matrix1.m21 * matrix1.m21 + matrix1.m22 * matrix1.m22;

			IOFunctions.println( angle + " " + diff );
			
			if ( diff < minError )
			{
				minError = diff;
				minAngle = angle;
			}
		}
		
		return minAngle;
	}
	
	/**
	 * Computes the rotation axis between two affine matrices, assuming that the first affine transform is the identity transform E. 
	 * The rotation axis is just a relative ratio between x,y and z, so x is set to 1.
	 * @param model - the second affine transformation 
	 * @return - the Vector containing the rotation axis
	 */
	public static Vector3d extractRotationAxis( final AffineModel3D model )
	{
		final double[] matrix = model.getMatrix( null );
				
		final double m00 = matrix[ 0 ];
		final double m01 = matrix[ 1 ];
		final double m02 = matrix[ 2 ];
		final double m10 = matrix[ 4 ];
		final double m11 = matrix[ 5 ];
		final double m12 = matrix[ 6 ];
		final double m20 = matrix[ 8 ];
		final double m21 = matrix[ 9 ];
		final double m22 = matrix[ 10 ];
		
		final Vector3d rotationAxis = new Vector3d( 1, 0, 0 );
		
		final double x = rotationAxis.x;		
		rotationAxis.y = ( ( 1 - m00 ) * ( 1 - m22 ) * x - m20 * m02 * x ) / ( m01 * ( 1 - m22 ) + m21 * m02 );
		rotationAxis.z = ( ( 1 - m00 ) * ( 1 - m11 ) * x - m10 * m01 * x ) / ( m02 * ( 1 - m11 ) + m12 * m01 );
		
		return rotationAxis;
		
		/*
		IOFunctions.println( modelA );
		IOFunctions.println( modelB );
		
		final float[] v1 = new float[ 3 ];
		v1[ 0 ] = -1;
		v1[ 1 ] = 0;
		v1[ 2 ] = 0;
		
		final float[] v2 = v1.clone();
		
		modelA.applyInPlace( v1 );
		modelB.applyInPlace( v2 );
		
		float sum = 0;
		for ( int j = 0; j < 3; ++j )
			sum += (v1[ j ] - v2[ j ]) * (v1[ j ] - v2[ j ]);
			
		IOFunctions.println( sum );
		*/
	}

	/**
	 * Computes the rotation axis between two affine matrices. 
	 * The rotation axis is just a relative ratio between x,y and z, so x is set to 1.
	 * @param modelA - the first affine transformation 
	 * @param modelB - the second affine transformation 
	 * @return - the Vector containing the rotation axis
	 */
	public static Vector3d extractRotationAxis( final AffineModel3D modelA, final AffineModel3D modelB )
	{
		final double[] matrixA = modelA.getMatrix( null );
		final double[] matrixB = modelB.getMatrix( null );
				
		final double m00 = matrixA[ 0 ];
		final double m01 = matrixA[ 1 ];
		final double m02 = matrixA[ 2 ];
		final double m10 = matrixA[ 4 ];
		final double m11 = matrixA[ 5 ];
		final double m12 = matrixA[ 6 ];
		final double m20 = matrixA[ 8 ];
		final double m21 = matrixA[ 9 ];
		final double m22 = matrixA[ 10 ];

		final double n00 = matrixB[ 0 ];
		final double n01 = matrixB[ 1 ];
		final double n02 = matrixB[ 2 ];
		final double n10 = matrixB[ 4 ];
		final double n11 = matrixB[ 5 ];
		final double n12 = matrixB[ 6 ];
		final double n20 = matrixB[ 8 ];
		final double n21 = matrixB[ 9 ];
		final double n22 = matrixB[ 10 ];
		
		final Vector3d rotationAxis = new Vector3d( 1, 0, 0 );
		
		final double x = rotationAxis.x;		
		rotationAxis.y = ( ( m00 - n00 ) * ( m22 - n22 ) * x - ( n20 - m20 ) * ( n02 - m02 ) * x ) / 
						 ( ( n01 - m01 ) * ( m22 - n22 ) + ( n21 - m21 ) * ( n02 - m02 ) );
		rotationAxis.z = ( ( m00 - n00 ) * ( m11 - n11 ) * x - ( n10 - m10 ) * ( n01 - m01 ) * x ) / 
						 ( ( n02 - m02 ) * ( m11 - n11 ) + ( n12 - m12 ) * ( n01 - m01 ) );
		
		return rotationAxis;
		
		/*
		IOFunctions.println( modelA );
		IOFunctions.println( modelB );
		
		final float[] v1 = new float[ 3 ];
		v1[ 0 ] = -1;
		v1[ 1 ] = 0;
		v1[ 2 ] = 0;
		
		final float[] v2 = v1.clone();
		
		modelA.applyInPlace( v1 );
		modelB.applyInPlace( v2 );
		
		float sum = 0;
		for ( int j = 0; j < 3; ++j )
			sum += (v1[ j ] - v2[ j ]) * (v1[ j ] - v2[ j ]);
			
		IOFunctions.println( sum );
		*/
	}

	public static Vector3d extractRotationAxis( final Matrix3d matrixA, final Matrix3d matrixB )
	{
		final double m00 = matrixA.m00;
		final double m01 = matrixA.m01;
		final double m02 = matrixA.m02;
		final double m10 = matrixA.m10;
		final double m11 = matrixA.m11;
		final double m12 = matrixA.m12;
		final double m20 = matrixA.m20;
		final double m21 = matrixA.m21;
		final double m22 = matrixA.m22;

		final double n00 = matrixB.m00;
		final double n01 = matrixB.m01;
		final double n02 = matrixB.m02;
		final double n10 = matrixB.m10;
		final double n11 = matrixB.m11;
		final double n12 = matrixB.m12;
		final double n20 = matrixB.m20;
		final double n21 = matrixB.m21;
		final double n22 = matrixB.m22;
		
		final Vector3d rotationAxis = new Vector3d( 1, 0, 0 );
		
		final double x = rotationAxis.x;		
		rotationAxis.y = ( ( m00 - n00 ) * ( m22 - n22 ) * x - ( n20 - m20 ) * ( n02 - m02 ) * x ) / 
						 ( ( n01 - m01 ) * ( m22 - n22 ) + ( n21 - m21 ) * ( n02 - m02 ) );
		rotationAxis.z = ( ( m00 - n00 ) * ( m11 - n11 ) * x - ( n10 - m10 ) * ( n01 - m01 ) * x ) / 
						 ( ( n02 - m02 ) * ( m11 - n11 ) + ( n12 - m12 ) * ( n01 - m01 ) );
		
		return rotationAxis;
		
		/*
		IOFunctions.println( modelA );
		IOFunctions.println( modelB );
		
		final float[] v1 = new float[ 3 ];
		v1[ 0 ] = -1;
		v1[ 1 ] = 0;
		v1[ 2 ] = 0;
		
		final float[] v2 = v1.clone();
		
		modelA.applyInPlace( v1 );
		modelB.applyInPlace( v2 );
		
		float sum = 0;
		for ( int j = 0; j < 3; ++j )
			sum += (v1[ j ] - v2[ j ]) * (v1[ j ] - v2[ j ]);
			
		IOFunctions.println( sum );
		*/
	}

	protected void getCommonAreaPerpendicularViews( final ViewDataBeads viewA, final ViewDataBeads viewB )
	{
		final double[][] minMaxDimViewA = TransformUtils.getMinMaxDim( viewA.getImageSize(), viewA.getTile().getModel() );
		final double[][] minMaxDimViewB = TransformUtils.getMinMaxDim( viewB.getImageSize(), viewB.getTile().getModel() );
		
		//final double minX = minMaxDimViewB[ 0 ][ 0 ];
		final double maxX = minMaxDimViewB[ 0 ][ 1 ];

		final double minY = minMaxDimViewB[ 1 ][ 0 ];
		final double maxY = minMaxDimViewB[ 1 ][ 1 ];

		final double minZ = minMaxDimViewA[ 2 ][ 0 ];
		final double maxZ = minMaxDimViewA[ 2 ][ 1 ];

		IOFunctions.println( "X1: " + minMaxDimViewA[ 0 ][ 0 ] + " -> " + minMaxDimViewA[ 0 ][ 1 ] );
		IOFunctions.println( "X2: " + minMaxDimViewB[ 0 ][ 0 ] + " -> " + minMaxDimViewB[ 0 ][ 1 ] );
		IOFunctions.println( "Y1: " + minMaxDimViewA[ 1 ][ 0 ] + " -> " + minMaxDimViewA[ 1 ][ 1 ] );
		IOFunctions.println( "Y2: " + minMaxDimViewB[ 1 ][ 0 ] + " -> " + minMaxDimViewB[ 1 ][ 1 ] );
		IOFunctions.println( "Z1: " + minMaxDimViewA[ 2 ][ 0 ] + " -> " + minMaxDimViewA[ 2 ][ 1 ] );
		IOFunctions.println( "Z2: " + minMaxDimViewB[ 2 ][ 0 ] + " -> " + minMaxDimViewB[ 2 ][ 1 ] );
		
		final double angle = Math.toRadians( 30 );
		
		final Point3d q = new Point3d( maxX, (maxY - minY)/2f, (maxZ - minZ)/2f );
		final Point3d r = new Point3d( 0, Math.cos( angle ), Math.sin( angle ) );
		
		final Point3d p1 = new Point3d( maxX, minY, minZ );
		final Point3d p2 = new Point3d( maxX, maxY, minZ );
		final Point3d p3 = new Point3d( maxX, minY, maxZ );
		final Point3d p4 = new Point3d( maxX, maxY, maxZ );
		
		// ebenengleichung
		
		double lambda1 = computeLambda( q, r, p1 );  
		double lambda2 = computeLambda( q, r, p2 );  
		double lambda3 = computeLambda( q, r, p3 );  
		double lambda4 = computeLambda( q, r, p4 );
		
		final Point3d fp1 = new Point3d( lambda1 * r.x + q.x, lambda1 * r.y + q.y, lambda1 * r.z + q.z );  
		final Point3d fp2 = new Point3d( lambda2 * r.x + q.x, lambda2 * r.y + q.y, lambda2 * r.z + q.z );  
		final Point3d fp3 = new Point3d( lambda3 * r.x + q.x, lambda3 * r.y + q.y, lambda3 * r.z + q.z );  
		final Point3d fp4 = new Point3d( lambda4 * r.x + q.x, lambda4 * r.y + q.y, lambda4 * r.z + q.z );
		
		final double d1 = Math.sqrt( Math.pow( p1.x - fp1.x, 2) + Math.pow( p1.y - fp1.y, 2) + Math.pow( p1.z - fp1.z, 2) ); 
		final double d2 = Math.sqrt( Math.pow( p2.x - fp2.x, 2) + Math.pow( p2.y - fp2.y, 2) + Math.pow( p2.z - fp2.z, 2) ); 
		final double d3 = Math.sqrt( Math.pow( p3.x - fp3.x, 2) + Math.pow( p3.y - fp3.y, 2) + Math.pow( p3.z - fp3.z, 2) ); 
		final double d4 = Math.sqrt( Math.pow( p4.x - fp4.x, 2) + Math.pow( p4.y - fp4.y, 2) + Math.pow( p4.z - fp4.z, 2) ); 
		
		
		IOFunctions.println( d1 + " " + d2 + " " + d3 + " " + d4 );
	}
	
	protected double computeLambda( final Point3d q, final Point3d r, final Point3d p )
	{
		return ( p.x + p.y + p.z - ( r.x*q.x + r.y*q.y + r.z*q.z ) ) / ( r.x*r.x + r.y*r.y + r.z*r.z );
	}

	public static void main( String[] args )
	{
		final SPIMConfiguration config = IOFunctions.initSPIMProcessing();
		
		//
		// load the files
		//
		final ViewStructure viewStructure = ViewStructure.initViewStructure( config, 0, new AffineModel3D(), "ViewStructure Timepoint " + 0, config.debugLevelInt );						

		for ( final ViewDataBeads view : viewStructure.getViews() )
		{
			view.loadDimensions();
			view.loadSegmentation();
			view.loadRegistration();
		}
		
		// This scaling is wrong here!
		// BeadRegistration.concatenateAxialScaling( viewStructure.getViews(), viewStructure.getDebugLevel() );		
		
		new AutomaticAngleSetup( viewStructure );
	}
}

