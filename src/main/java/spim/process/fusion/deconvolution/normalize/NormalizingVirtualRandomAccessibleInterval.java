package spim.process.fusion.deconvolution.normalize;

import java.util.ArrayList;

import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class NormalizingVirtualRandomAccessibleInterval< T extends RealType< T > > implements RandomAccessibleInterval< T >
{
	final int index;
	final RandomAccessibleInterval< T > interval;
	final ArrayList< RandomAccessibleInterval< FloatType > > originalWeights;
	double osemspeedup;
	final boolean additionalSmoothBlending;
	final float maxDiffRange;
	final float scalingRange;
	final T type;

	public NormalizingVirtualRandomAccessibleInterval(
			final RandomAccessibleInterval< T > interval,
			final int index,
			final ArrayList< RandomAccessibleInterval< FloatType > > originalWeights,
			final double osemspeedup,
			final boolean additionalSmoothBlending,
			final float maxDiffRange,
			final float scalingRange,
			final T type )
	{
		// the assumption is that dimensionality & size matches, we do not test it tough
		this.interval = interval;
		this.index = index;
		this.originalWeights = originalWeights;
		this.osemspeedup = osemspeedup;
		this.additionalSmoothBlending = additionalSmoothBlending;
		this.maxDiffRange = maxDiffRange;
		this.scalingRange = scalingRange;
		this.type = type;
	}

	public void setOSEMspeedup( final double osemspeedup ) { this.osemspeedup = osemspeedup; }
	public double getOSEMspeedup() { return osemspeedup; }

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new NormalizingVirtualRandomAccess< T >( index, originalWeights, osemspeedup, additionalSmoothBlending, maxDiffRange, scalingRange, type );
	}

	@Override
	public int numDimensions() { return interval.numDimensions(); }

	@Override
	public RandomAccess< T > randomAccess( final Interval interval ) { return randomAccess(); }

	@Override
	public long min( final int d ){ return interval.min( 0 ); }

	@Override
	public void min( final long[] min ) { interval.min( min ); }

	@Override
	public void min( final Positionable min ) { interval.min( min ); }

	@Override
	public long max( final int d ) { return interval.max( d ); }

	@Override
	public void max( final long[] max ) { interval.max( max ); }

	@Override
	public void max( final Positionable max ) { interval.max( max ); }

	@Override
	public double realMin( final int d ) { return interval.realMin( d ); }

	@Override
	public void realMin( final double[] min ) { interval.realMin( min ); }

	@Override
	public void realMin( final RealPositionable min ) { interval.realMin( min ); }

	@Override
	public double realMax( final int d ) { return interval.realMax( d ); }

	@Override
	public void realMax( final double[] max ) { interval.realMax( max ); }

	@Override
	public void realMax( final RealPositionable max ) { interval.realMax( max ); }

	@Override
	public void dimensions( final long[] dimensions ) { interval.dimensions( dimensions ); }

	@Override
	public long dimension( final int d ) { return interval.dimension( d ); }
}
