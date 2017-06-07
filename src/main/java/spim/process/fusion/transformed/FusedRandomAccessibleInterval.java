package spim.process.fusion.transformed;

import java.util.List;

import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.type.numeric.real.FloatType;

public class FusedRandomAccessibleInterval implements RandomAccessibleInterval< FloatType >
{
	final int n;

	final Interval interval;
	final List< RandomAccessibleInterval< FloatType > > images;
	final List< RandomAccessibleInterval< FloatType > > weights;

	public FusedRandomAccessibleInterval(
			final Interval interval,
			final List< RandomAccessibleInterval< FloatType > > images,
			final List< RandomAccessibleInterval< FloatType > > weights )
	{
		this.n = interval.numDimensions();
		this.interval = interval;
		this.images = images;
		this.weights = weights;
	}

	public List< RandomAccessibleInterval< FloatType > > getImages() { return images; }
	public List< RandomAccessibleInterval< FloatType > > getWeights() { return weights; }

	@Override
	public int numDimensions()
	{
		return n;
	}

	@Override
	public RandomAccess< FloatType > randomAccess()
	{
		return new FusedRandomAccess( n, images, weights );
	}

	@Override
	public RandomAccess< FloatType > randomAccess( Interval interval )
	{
		return randomAccess();
	}

	@Override
	public long min( final int d ) { return interval.min( d ); }

	@Override
	public void min( final long[] min ) { interval.min( min ); }

	@Override
	public void min( final Positionable min ) { interval.min( min ); }

	@Override
	public long max( final int d ) { return interval.min( d ); }

	@Override
	public void max( final long[] max ) { interval.max( max ); }

	@Override
	public void max( final Positionable max )  { interval.max( max ); }

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
