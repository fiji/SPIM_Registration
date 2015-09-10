package spim.process.fusion.weights;

import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Util;

public class TransformedRealRandomAccessibleInterval< T > implements RandomAccessibleInterval< T >
{
	final RealRandomAccessible< T > realRandomAccessible;
	final T zero;
	final Interval transformedInterval;
	final AffineTransform3D transform;
	final long[] offset;

	/**
	 * @param realRandomAccessible - some {@link RealRandomAccessible} that we transform
	 * @param transformedInterval - the interval after applying the transform that it is defined on
	 * @param transform - the affine transformation
	 * @param offset - an additional translational offset
	 */
	public TransformedRealRandomAccessibleInterval(
			final RealRandomAccessible< T > realRandomAccessible,
			final T zero,
			final Interval transformedInterval,
			final AffineTransform3D transform,
			final long[] offset )
	{
		this.realRandomAccessible = realRandomAccessible;
		this.zero = zero;
		this.transformedInterval = transformedInterval;
		this.transform = transform;
		this.offset = offset;
	}

	@Override
	public int numDimensions() { return realRandomAccessible.numDimensions(); }

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new TransformedInterpolatedRealRandomAccess< T >( realRandomAccessible, zero, transformedInterval, transform, Util.long2int( offset ) );
	}

	@Override
	public RandomAccess< T > randomAccess( final Interval interval ) { return randomAccess(); }

	@Override
	public long min( final int d ){ return transformedInterval.min( d ); }

	@Override
	public void min( final long[] min ) { transformedInterval.min( min ); }

	@Override
	public void min( final Positionable min ) { transformedInterval.min( min ); }

	@Override
	public long max( final int d ) { return transformedInterval.max( d ); }

	@Override
	public void max( final long[] max ) { transformedInterval.max( max ); }

	@Override
	public void max( final Positionable max ) { transformedInterval.max( max ); }

	@Override
	public double realMin( final int d ) { return transformedInterval.realMin( d ); }

	@Override
	public void realMin( final double[] min ) { transformedInterval.realMin( min ); }

	@Override
	public void realMin( final RealPositionable min ) { transformedInterval.realMin( min ); }

	@Override
	public double realMax( final int d ) { return transformedInterval.realMax( d ); }

	@Override
	public void realMax( final double[] max ) { transformedInterval.realMax( max ); }

	@Override
	public void realMax( final RealPositionable max ) { transformedInterval.realMax( max ); }

	@Override
	public void dimensions( final long[] dimensions ) { transformedInterval.dimensions( dimensions ); }

	@Override
	public long dimension( final int d ) { return transformedInterval.dimension( d ); }
}
