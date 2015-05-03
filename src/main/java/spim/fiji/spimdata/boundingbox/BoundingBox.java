package spim.fiji.spimdata.boundingbox;

import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RealPositionable;

public class BoundingBox implements Interval, Comparable< BoundingBox >
{
	protected int[] min, max;
	protected String title;

	public BoundingBox( final String title, final int[] min, final int[] max )
	{
		this.title = title;
		this.min = min;
		this.max = max;
	}

	public BoundingBox( final int[] min, final int[] max )
	{
		this.min = min;
		this.max = max;
		this.title = "DefaultBoundingBox";
	}

	public void setTitle( final String title ) { this.title = title; }
	public String getTitle() { return title; }

	public int[] getMin() { return min; }
	public int[] getMax() { return max; }

	@Override
	public long min( final int d ) { return min[ d ]; }

	@Override
	public void min( final long[] min )
	{
		for ( int d = 0; d < min.length; ++d )
			min[ d ] = this.min[ d ];
	}

	@Override
	public void min( final Positionable min ) { min.setPosition( this.min ); }

	@Override
	public long max( final int d ) { return max[ d ]; }

	@Override
	public void max( final long[] max )
	{
		for ( int d = 0; d < max.length; ++d )
			max[ d ] = this.max[ d ];
	}

	@Override
	public void max( final Positionable max ) { max.setPosition( this.max ); }

	@Override
	public double realMin( final int d ) { return min[ d ]; }

	@Override
	public void realMin( final double[] min )
	{
		for ( int d = 0; d < min.length; ++d )
			min[ d ] = this.min[ d ];
	}

	@Override
	public void realMin( final RealPositionable min ) { min.setPosition( this.min ); }

	@Override
	public double realMax( final int d ) { return this.max[ d ]; }

	@Override
	public void realMax( final double[] max )
	{
		for ( int d = 0; d < max.length; ++d )
			max[ d ] = this.max[ d ];
	}

	@Override
	public void realMax( final RealPositionable max ) { max.setPosition( this.max ); }

	@Override
	public int numDimensions() { return min.length; }

	@Override
	public void dimensions( final long[] dimensions )
	{
		for ( int d = 0; d < max.length; ++d )
			dimensions[ d ] = dimension( d );
	}

	@Override
	public long dimension( final int d ) { return this.max[ d ] - this.min[ d ] + 1; }

	@Override
	public int compareTo( final BoundingBox o ) { return o.getTitle().compareTo( this.getTitle() ); }
}
