package mpicbg.pointdescriptor.test;

import fiji.util.node.Leaf;
import mpicbg.models.Point;

public class PointNode extends Point implements Leaf<PointNode>
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	final int numDimensions;
	
	public PointNode( final Point p )
	{
		super( p.getL().clone(), p.getW().clone() );
		this.numDimensions = l.length;
	}
	
	public PointNode( final double[] l )
	{
		super( l );
		this.numDimensions = l.length;
	}

	public PointNode( final double[] l, final double[] w )
	{
		super( l, w );
		this.numDimensions = l.length;
	}

	@Override
	public PointNode[] createArray( final int n ) { return new PointNode[ n ]; }

	@Override
	public float distanceTo( final PointNode other ) { return (float)Point.distance( this, other ); }

	@Override
	public float get( final int k ) { return (float)w[ k ]; }

	@Override
	public int getNumDimensions() { return numDimensions; }

	@Override
	public boolean isLeaf() { return true; }	
}
