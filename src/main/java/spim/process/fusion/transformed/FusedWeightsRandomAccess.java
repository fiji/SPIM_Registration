package spim.process.fusion.transformed;

import java.util.List;

import net.imglib2.AbstractLocalizableInt;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class FusedWeightsRandomAccess extends AbstractLocalizableInt implements RandomAccess< FloatType >
{
	final List< RandomAccessibleInterval< FloatType > > weights;
	final int numImages;
	final RandomAccess< ? extends RealType< ? > >[] w;

	final FloatType value = new FloatType();

	public FusedWeightsRandomAccess(
			final int n,
			final List< RandomAccessibleInterval< FloatType > > weights )
	{
		super( n );

		this.weights = weights;
		this.numImages = weights.size();
		this.w = new RandomAccess[ numImages ];

		for ( int j = 0; j < numImages; ++j )
			this.w[ j ] = weights.get( j ).randomAccess();
	}

	@Override
	public FloatType get()
	{
		double sumW = 0;

		for ( int j = 0; j < numImages; ++j )
		{
			final double weight = w[ j ].get().getRealDouble();
			sumW += weight;
		}

		value.set( (float)sumW );

		return value;
	}

	@Override
	public FusedWeightsRandomAccess copy()
	{
		return copyRandomAccess();
	}

	@Override
	public FusedWeightsRandomAccess copyRandomAccess()
	{
		final FusedWeightsRandomAccess r = new FusedWeightsRandomAccess( n, weights );
		r.setPosition( this );
		return r;
	}

	@Override
	public void fwd( final int d )
	{
		++position[ d ];

		for ( int j = 0; j < numImages; ++j )
			w[ j ].fwd( d );
	}

	@Override
	public void bck( final int d )
	{
		--position[ d ];

		for ( int j = 0; j < numImages; ++j )
			w[ j ].bck( d );
	}

	@Override
	public void move( final int distance, final int d )
	{
		position[ d ] += distance;

		for ( int j = 0; j < numImages; ++j )
			w[ j ].move( distance, d );
	}

	@Override
	public void move( final long distance, final int d )
	{
		position[ d ] += distance;

		for ( int j = 0; j < numImages; ++j )
			w[ j ].move( distance, d );
	}

	@Override
	public void move( final Localizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += localizable.getIntPosition( d );

		for ( int j = 0; j < numImages; ++j )
			w[ j ].move( localizable );
	}

	@Override
	public void move( final int[] distance )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += distance[ d ];

		for ( int j = 0; j < numImages; ++j )
			w[ j ].move( distance );
	}

	@Override
	public void move( final long[] distance )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += distance[ d ];

		for ( int j = 0; j < numImages; ++j )
			w[ j ].move( distance );
	}

	@Override
	public void setPosition( final Localizable localizable )
	{
		localizable.localize( position );

		for ( int j = 0; j < numImages; ++j )
			w[ j ].setPosition( localizable );
	}

	@Override
	public void setPosition( final int[] pos )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = pos[ d ];

		for ( int j = 0; j < numImages; ++j )
			w[ j ].setPosition( pos );
	}

	@Override
	public void setPosition( final long[] pos )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = ( int ) pos[ d ];

		for ( int j = 0; j < numImages; ++j )
			w[ j ].setPosition( pos );
	}

	@Override
	public void setPosition( final int pos, final int d )
	{
		position[ d ] = pos;

		for ( int j = 0; j < numImages; ++j )
			w[ j ].setPosition( pos, d );
	}

	@Override
	public void setPosition( final long pos, final int d )
	{
		position[ d ] = ( int ) pos;

		for ( int j = 0; j < numImages; ++j )
			w[ j ].setPosition( pos, d );
	}
}
