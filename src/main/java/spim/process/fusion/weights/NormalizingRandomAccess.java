package spim.process.fusion.weights;

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

	public NormalizingRandomAccess(
			final RandomAccessibleInterval< T > interval,
			final RandomAccessibleInterval< T > normalizeInterval,
			final double osemspeedup,
			final T type )
	{
		super( interval.numDimensions() );

		this.interval = interval;
		this.normalizeInterval = normalizeInterval;
		this.type = type.createVariable();
		this.osemspeedup = osemspeedup;

		this.intervalRandomAccess = interval.randomAccess();
		this.normalizeIntervalRandomAccess = normalizeInterval.randomAccess();
	}

	@Override
	public T get()
	{
		intervalRandomAccess.setPosition( position );
		normalizeIntervalRandomAccess.setPosition( position );

		final double v = intervalRandomAccess.get().getRealDouble() / normalizeIntervalRandomAccess.get().getRealDouble();
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
	public NormalizingRandomAccess< T > copy() { return new NormalizingRandomAccess< T >( interval, normalizeInterval, osemspeedup, type ); }

	@Override
	public NormalizingRandomAccess<T> copyRandomAccess() { return copy(); }
}
