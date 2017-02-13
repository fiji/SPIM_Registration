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

	/** Use world coordinates instead of local coordinates to localize */
	protected boolean useW;

	public InterestPoint( final int id, final double[] l )
	{
		super( l );
		this.id = id;
	}

	public int getId() { return id; }
	public void setUseW( final boolean useW ) { this.useW = useW; }
	public boolean getUseW() { return useW; }

	@Override
	public int numDimensions() { return l.length; }

	@Override
	public void localize( final float[] position )
	{
		for ( int d = 0; d < l.length; ++d )
			position[ d ] = useW? (float)w[ d ] : (float)l[ d ];
	}

	@Override
	public void localize( final double[] position )
	{
		for ( int d = 0; d < l.length; ++d )
			position[ d ] = useW? w[ d ] : l[ d ];
	}

	@Override
	public float getFloatPosition( final int d ) { return useW? (float)w[ d ] : (float)l[ d ]; }

	@Override
	public double getDoublePosition( final int d ) { return useW? w[ d ] : l[ d ]; }

	public InterestPoint newInstance( final int id, final double[] l ) { return new InterestPoint( id, l ); }
}
