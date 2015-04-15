package spim.fiji.spimdata.interestpoints;

import net.imglib2.RealLocalizable;
import mpicbg.models.Point;

/**
 * Single interest point, extends mpicbg Point by an id
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class InterestPoint extends Point implements RealLocalizable
{
	private static final long serialVersionUID = 5615112297702152070L;

	protected final int id;

	public InterestPoint( final int id, final double[] l )
	{
		super( l );
		this.id = id;
	}
	
	public int getId() { return id; }

	@Override
	public int numDimensions() { return l.length; }

	@Override
	public void localize( final float[] position )
	{
		for ( int d = 0; d < l.length; ++d )
			position[ d ] = (float)l[ d ];
	}

	@Override
	public void localize( final double[] position )
	{
		for ( int d = 0; d < l.length; ++d )
			position[ d ] = l[ d ];
	}

	@Override
	public float getFloatPosition( final int d ) { return (float)l[ d ]; }

	@Override
	public double getDoublePosition( final int d ) { return l[ d ]; }
}
