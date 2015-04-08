package spim.process.fusion.weights;

import spim.process.fusion.FusionHelper;
import net.imglib2.AbstractLocalizableInt;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;

public class TransformedRandomAccess< T > extends AbstractLocalizableInt implements RandomAccess< T >
{
	final RealRandomAccessible< T > realRandomAccessible;
	final Interval transformedInterval;
	final AffineTransform3D transform;
	final int[] offset;
	final int[] imgSize;

	final float[] tmp;

	public TransformedRandomAccess(
			final RealRandomAccessible< T > realRandomAccessible,
			final Interval transformedInterval,
			final AffineTransform3D transform,
			final int[] offset )
	{
		super( realRandomAccessible.numDimensions() );

		this.realRandomAccessible = realRandomAccessible;
		this.transformedInterval = transformedInterval;
		this.transform = transform;
		this.offset = offset;

		this.imgSize = new int[ n ];

		for ( int d = 0; d < n; ++d )
			this.imgSize[ d ] = (int)transformedInterval.dimension( d );

		this.tmp = new float[ n ];

		final Cursor< FloatType > cursorB = blendingImg.cursor();

		final float[] s = new float[ 3 ];
		final float[] t = new float[ 3 ];
		
		cursorB.jumpFwd( portion.getStartPosition() );
		
		for ( int j = 0; j < portion.getLoopSize(); ++j )
		{
			// move img cursor forward any get the value (saves one access)
			final FloatType o = cursorO.next();
			cursorO.localize( s );
			
			// move weight cursor forward and get the value 
			final FloatType b = cursorB.next();

			s[ 0 ] += bb.min( 0 );
			s[ 1 ] += bb.min( 1 );
			s[ 2 ] += bb.min( 2 );
			
			transform.applyInverse( t, s );
			
			if ( FusionHelper.intersects( t[ 0 ], t[ 1 ], t[ 2 ], imgSize[ 0 ], imgSize[ 1 ], imgSize[ 2 ] ) )
			{
				wr.setPosition( t );
				
				o.set( o.get() + 1 );
				b.set( wr.get() );
			}
		}
		
	}

	@Override
	public T get() {
		// TODO Auto-generated method stub
		return null;
	}

	private static final boolean intersects3d( final double x, final double y, final double z, final long sx, final long sy, final long sz )
	{
		if ( x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz )
			return true;
		else
			return false;
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
			this.position[ d ] = localizable.getIntPosition( d ) + offset[ d ];
	}

	@Override
	public void setPosition( final int[] position )
	{
		for ( int d = 0; d < n; ++d )
			this.position[ d ] = position[ d ] + offset[ d ];
	}

	@Override
	public void setPosition( final long[] position )
	{
		for ( int d = 0; d < n; ++d )
			this.position[ d ] = (int)position[ d ] + offset[ d ];
	}

	@Override
	public void setPosition( final int position, final int d ) { this.position[ d ] = position + offset[ d ]; }

	@Override
	public void setPosition( final long position, final int d ) { this.position[ d ] = (int)position + offset[ d ]; }

	@Override
	public TransformedRandomAccess< T > copy() { return new TransformedRandomAccess< T >( realRandomAccessible, transformedInterval, transform, offset ); }

	@Override
	public TransformedRandomAccess<T> copyRandomAccess() { return copy(); }

}
