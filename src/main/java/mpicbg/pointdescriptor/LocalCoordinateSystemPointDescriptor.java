package mpicbg.pointdescriptor;

import java.util.ArrayList;

import spim.vecmath.Matrix3d;
import spim.vecmath.Vector3d;

import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import fiji.util.node.Leaf;

public class LocalCoordinateSystemPointDescriptor < P extends Point > extends AbstractPointDescriptor< P, LocalCoordinateSystemPointDescriptor<P> > 
		implements Leaf< LocalCoordinateSystemPointDescriptor<P> >
{
	final protected boolean normalize;
	public float ax = 1, bx, by, cx, cy, cz;
	
	public LocalCoordinateSystemPointDescriptor( final P basisPoint, final ArrayList<P> orderedNearestNeighboringPoints,  
												 final boolean normalize ) throws NoSuitablePointsException 
	{
		super( basisPoint, orderedNearestNeighboringPoints, null, null );
		
		if ( numDimensions != 3 )
			throw new NoSuitablePointsException( "LocalCoordinateSystemPointDescriptor does not support dim = " + numDimensions + ", only dim = 3 is valid." );

		/* check that number of points is at least model.getMinNumMatches() */
		if ( numNeighbors() != 3 )
			throw new NoSuitablePointsException( "Only 3 nearest neighbors is supported by a LocalCoordinateSystemPointDescriptor : num neighbors = " + numNeighbors() );
	
		this.normalize = normalize;
		
		buildLocalCoordinateSystem( descriptorPoints, normalize );
	}

	@Override
	public double descriptorDistance( final LocalCoordinateSystemPointDescriptor< P > pointDescriptor ) 
	{ 
		double difference = 0;
		
		if ( !normalize )
			difference += ( ax - pointDescriptor.ax ) * ( ax - pointDescriptor.ax );  	
		
		difference += ( bx - pointDescriptor.bx ) * ( bx - pointDescriptor.bx );  
		difference += ( by - pointDescriptor.by ) * ( by - pointDescriptor.by );  
		difference += ( cx - pointDescriptor.cx ) * ( cx - pointDescriptor.cx );  
		difference += ( cy - pointDescriptor.cy ) * ( cy - pointDescriptor.cy );  
		difference += ( cz - pointDescriptor.cz ) * ( cz - pointDescriptor.cz );
		
		return difference;// / 3.0;	
	}
	
	/**
	 * Not necessary as the main matching method is overwritten
	 */
	@Override
	public Object fitMatches( final ArrayList<PointMatch> matches )  { return null; }
	
	public void buildLocalCoordinateSystem( final ArrayList< LinkedPoint< P > > neighbors, final boolean normalize )
	{
		// most distant point		
		final Vector3d b = new Vector3d( neighbors.get( 0 ).getL() );
		final Vector3d c = new Vector3d( neighbors.get( 1 ).getL() );
		final Vector3d d = new Vector3d( neighbors.get( 2 ).getL() );
		
		final Vector3d x = new Vector3d( d );
		x.normalize();			

//		IOFunctions.println( "Input" );
//		IOFunctions.println( b );
//		IOFunctions.println( c );
//		IOFunctions.println( d );

		if ( normalize )
		{			
			final double lengthD = 1.0 / d.length();

			b.scale(lengthD);
			c.scale(lengthD);
			d.scale(lengthD);
			
//			IOFunctions.println( "Scaled" );
//			IOFunctions.println( b + "(" + b.length() + ")");
//			IOFunctions.println( c + "(" + c.length() + ")");
//			IOFunctions.println( d + "(" + d.length() + ")");
		}
		else
		{
			ax = (float)d.length();
		}
		
		// get normal vector of ab and ad ( which will be the z-axis)
		final Vector3d n = new Vector3d();
		n.cross(b, x);		
		n.normalize();
		
//		IOFunctions.println( "Normal vector (z-axis)" );
//		IOFunctions.println( n );

		// check if the normal vector points into the direction of point c
		if ( n.dot( c ) < 0 )
		{
			n.negate();
//			IOFunctions.println( "Negated normal vector (z-axis)" );
//			IOFunctions.println( n );
		}
		
		// get the inverse of the matrix that maps the vectors into the local coordinate system
		// where the x-axis is vector(ad), the z-axis is n and the y-axis is cross-product(x,z)
		final Vector3d y = new Vector3d();
		y.cross( n, x );
		y.normalize();
		
//		IOFunctions.println( "X - axis" );		
//		IOFunctions.println( x );
//
//		IOFunctions.println( "Y - axis" );
//		IOFunctions.println( y );
		
		final Matrix3d m = new Matrix3d();
		m.m00 = x.x; m.m01 = y.x; m.m02 = n.x;  
		m.m10 = x.y; m.m11 = y.y; m.m12 = n.y; 
		m.m20 = x.z; m.m21 = y.z; m.m22 = n.z;
		
		try
		{
			m.invert();
		}
		catch ( Exception e )
		{
			bx = by = cx = cy = cz = 0;
			return;
		}
		
		
		// get the positions in the local coordinate system
		final Vector3d bl = new Vector3d( b );
		final Vector3d cl = new Vector3d( c );

		m.transform( bl );
		m.transform( cl );
		
//		IOFunctions.println( "In local coordinate system" );
//		IOFunctions.println( bl );
//		IOFunctions.println( cl );
		
		bx = (float)bl.x;
		by = (float)bl.y;
		cx = (float)cl.x;
		cy = (float)cl.y;
		cz = (float)cl.z;
		
//		System.out.println( "NEW" );
//		System.out.println( ax );
//		System.out.println( bx );
//		System.out.println( by );
//		System.out.println( cx );
//		System.out.println( cy );
//		System.out.println( cz );
//		
//		System.exit( 0 );
	}

	@SuppressWarnings("unchecked")
	@Override
	public LocalCoordinateSystemPointDescriptor<P>[] createArray( final int n ) 
	{
		return new LocalCoordinateSystemPointDescriptor[ n ];
	}

	@Override
	public float distanceTo( final LocalCoordinateSystemPointDescriptor<P> other ) 
	{		
		return (float) Math.sqrt( descriptorDistance( other ) );
	}

	@Override
	public float get( final int k ) 
	{
		if ( normalize )
		{
			if ( k == 0 )
				return bx;
			else if ( k == 1 )
				return by;
			else if ( k == 2 )
				return cx;
			else if ( k == 3 )
				return cy;
			else
				return cz;
		}
		else
		{
			if ( k == 0 )
				return ax;
			else if ( k == 1 )
				return bx;
			else if ( k == 2 )
				return by;
			else if ( k == 3 )
				return cx;
			else if ( k == 4 )
				return cy;
			else
				return cz;			
		}
	}

	@Override
	public int getNumDimensions() 
	{
		if ( normalize )
			return 5;
		else
			return 6;
	}

	@Override
	public boolean isLeaf() { return true; }

	@Override
	public boolean resetWorldCoordinatesAfterMatching() { return true; }

	@Override
	public boolean useWorldCoordinatesForDescriptorBuildUp() { return false; }
}
