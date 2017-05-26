package spim.fiji.spimdata.interestpoints;

import mpicbg.models.Point;
import net.imglib2.RealLocalizable;

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

	/** Report world coordinates or local coordinates to RealLocalizable */
	protected boolean useW;

	public InterestPoint( final int id, final double[] l )
	{
		this( id, l, true );
	}

	public InterestPoint( final int id, final double[] l, final boolean useW )
	{
		super( l );
		this.id = id;
		this.useW = true;
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

	public InterestPoint duplicate() { return clone(); }

	@Override
	public InterestPoint clone() { return new InterestPoint( this.id, this.l ); }
}
