package spim.process.fusion.weights;

import spim.process.fusion.deconvolution.WeightNormalizer;
import net.imglib2.AbstractLocalizableInt;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;

public class NormalizingRandomAccess< T extends RealType< T > > extends AbstractLocalizableInt implements RandomAccess< T >
{
	final RandomAccessibleInterval< T > interval;
	final RandomAccessibleInterval< T > normalizeInterval;
	final RandomAccess< T > intervalRandomAccess;
	final RandomAccess< T > normalizeIntervalRandomAccess;
	final T type;
	final double osemspeedup;
	final boolean additionalSmoothBlending;
	final float maxDiffRange;
	final float scalingRange;

	public NormalizingRandomAccess(
			final RandomAccessibleInterval< T > interval,
			final RandomAccessibleInterval< T > normalizeInterval,
			final double osemspeedup,
			final boolean additionalSmoothBlending,
			final float maxDiffRange,
			final float scalingRange,
			final T type )
	{
		super( interval.numDimensions() );

		this.interval = interval;
		this.normalizeInterval = normalizeInterval;
		this.type = type.createVariable();
		this.osemspeedup = osemspeedup;

		this.additionalSmoothBlending = additionalSmoothBlending;
		this.maxDiffRange = maxDiffRange;
		this.scalingRange = scalingRange;

		this.intervalRandomAccess = interval.randomAccess();
		this.normalizeIntervalRandomAccess = normalizeInterval.randomAccess();
	}

	@Override
	public T get()
	{
		intervalRandomAccess.setPosition( position );
		normalizeIntervalRandomAccess.setPosition( position );

		final double sumW = normalizeIntervalRandomAccess.get().getRealDouble();
		final double v;

		if ( additionalSmoothBlending )
		{
			v = WeightNormalizer.smoothWeights( intervalRandomAccess.get().getRealFloat(), sumW, maxDiffRange, scalingRange );
		}
		else if ( sumW > 1 )
		{
			v = intervalRandomAccess.get().getRealDouble() / sumW;
		}
		else
		{
			v = intervalRandomAccess.get().getRealDouble();
		}

		type.setReal( Math.min( 1, v * osemspeedup ) ); // individual contribution never higher than 1

		return type;
	}

	@Override
	public void fwd( final int d ) { ++this.position[ d ]; }

	@Override
	public void bck( final int d ) { --this.position[ d ]; }

	@Override
	public void move( final int distance, final int d ) { this.position[ d ] += distance; }

	@Override
	public void move( final long distance, final int d ) { this.position[ d ] += (int)distance; }

	@Override
	public void move( final Localizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			this.position[ d ] += localizable.getIntPosition( d );
	}

	@Override
	public void move( final int[] distance )
	{
		for ( int d = 0; d < n; ++d )
			this.position[ d ] += distance[ d ];
	}

	@Override
	public void move( final long[] distance )
	{
		for ( int d = 0; d < n; ++d )
			this.position[ d ] += (int)distance[ d ];
	}

	@Override
	public void setPosition( final Localizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			this.position[ d ] = localizable.getIntPosition( d );
	}

	@Override
	public void setPosition( final int[] position )
	{
		for ( int d = 0; d < n; ++d )
			this.position[ d ] = position[ d ];
	}

	@Override
	public void setPosition( final long[] position )
	{
		for ( int d = 0; d < n; ++d )
			this.position[ d ] = (int)position[ d ];
	}

	@Override
	public void setPosition( final int position, final int d ) { this.position[ d ] = position; }

	@Override
	public void setPosition( final long position, final int d ) { this.position[ d ] = (int)position; }

	@Override
	public NormalizingRandomAccess< T > copy() { return new NormalizingRandomAccess< T >( interval, normalizeInterval, osemspeedup, additionalSmoothBlending, maxDiffRange, scalingRange, type ); }

	@Override
	public NormalizingRandomAccess<T> copyRandomAccess() { return copy(); }
}
