package spim.fiji.plugin.interestpointregistration;

import mpicbg.imglib.util.Util;
import mpicbg.models.Point;
import fiji.util.node.Leaf;

public class Detection extends Point implements Leaf< Detection >
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1512879446587557778L;
	
	final protected long id;
	protected double weight;
	protected boolean useW = false;

	// used for display
	protected float distance = -1;

	// used for recursive parsing
	protected boolean isUsed = false;		
	
	public Detection( final int id, final float[] location )
	{
		super( location );
		this.id = id;
	}

	public Detection( final int id, final float[] location, final double weight )
	{
		super( location );
		this.id = id;
		this.weight = weight;
	}

	public void setWeight( final double weight ){ this.weight = weight; }
	public double getWeight(){ return weight; }
	public long getID() { return id; }
	public void setDistance( float distance )  { this.distance = distance; }
	public float getDistance() { return distance; }
	public boolean isUsed() { return isUsed; }
	public void setUsed( final boolean isUsed ) { this.isUsed = isUsed; }

	public boolean equals( final Detection otherDetection )
	{
		if ( useW )
		{
			for ( int d = 0; d < 3; ++d )
				if ( w[ d ] != otherDetection.w[ d ] )
					return false;			
		}
		else
		{
			for ( int d = 0; d < 3; ++d )
				if ( l[ d ] != otherDetection.l[ d ] )
					return false;						
		}
				
		return true;
	}
	
	public void setW( final float[] wn )
	{
		for ( int i = 0; i < w.length; ++i )
			w[ i ] = wn[ i ];
	}
	
	public void resetW()
	{
		for ( int i = 0; i < w.length; ++i )
			w[i] = l[i];
	}
	
	public double getDistance( final Point point2 )
	{
		double distance = 0;
		final float[] a = getL();
		final float[] b = point2.getW();
		
		for ( int i = 0; i < getL().length; ++i )
		{
			final double tmp = a[ i ] - b[ i ];
			distance += tmp * tmp;
		}
		
		return Math.sqrt( distance );
	}
	
	@Override
	public boolean isLeaf() { return true; }

	@Override
	public float distanceTo( final Detection o ) 
	{
		final float x = o.get( 0 ) - get( 0 );
		final float y = o.get( 1 ) - get( 1 );
		final float z = o.get( 2 ) - get( 2 );
		
		return (float)Math.sqrt(x*x + y*y + z*z);
	}
	
	public void setUseW( final boolean useW ) { this.useW = useW; } 
	public boolean getUseW() { return useW; } 

	@Override
	public float get( final int k ) 
	{
		if ( useW )
			return w[ k ];
		else
			return l[ k ];
	}
	
	@Override
	public int getNumDimensions() { return 3; }	
	
	@Override
	public String toString()
	{
		String desc = "Detection " + getID() + " l"+ Util.printCoordinates( getL() ) + "; w"+ Util.printCoordinates( getW() );
		return desc;
	}

	@Override
	public Detection[] createArray( final int n ) { return new Detection[ n ]; }	
}
