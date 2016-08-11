package spim.process.fusion.transformed;

import java.util.List;

import net.imglib2.AbstractLocalizableInt;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class FusedRandomAccessNoWeights extends AbstractLocalizableInt implements RandomAccess< FloatType >
{
	final List< RandomAccessibleInterval< ? extends RealType< ? > > > images;
	final List< RandomAccessibleInterval< ? extends RealType< ? > > > weights;

	final int numImages;
	final RandomAccess< ? extends RealType< ? > >[] i, w;

	final FloatType value = new FloatType();

	public FusedRandomAccessNoWeights(
			final int n,
			final List< RandomAccessibleInterval< ? extends RealType< ? > > > images,
			final List< RandomAccessibleInterval< ? extends RealType< ? > > > weights )
	{
		super( n );

		this.images = images;
		this.weights = weights;

		this.numImages = images.size();

		this.i = new RandomAccess[ numImages ];
		this.w = new RandomAccess[ numImages ];

		for ( int j = 0; j < numImages; ++j )
		{
			this.i[ j ] = images.get( j ).randomAccess();
			this.w[ j ] = weights.get( j ).randomAccess();
		}
	}

	@Override
	public FloatType get()
	{
		double sumI = 0;
		double sumW = 0;

		for ( int j = 0; j < numImages; ++j )
		{
			final double weight = w[ j ].get().getRealDouble();
			final double intensity = i[ j ].get().getRealDouble();

			sumI += intensity * weight;
			sumW += weight;
		}

		if ( sumW > 0 )
			value.set( (float)( sumI / sumW ) );
		else
			value.set(  0 );

		return value;
	}

	@Override
	public FusedRandomAccessNoWeights copy()
	{
		return copyRandomAccess();
	}

	@Override
	public FusedRandomAccessNoWeights copyRandomAccess()
	{
		final FusedRandomAccessNoWeights r = new FusedRandomAccessNoWeights( n, images, weights );
		r.setPosition( this );
		return r;
	}

	@Override
	public void fwd( final int d ) { ++position[ d ]; }

	@Override
	public void bck( final int d ) { --position[ d ]; }

	@Override
	public void move( final int distance, final int d ) { position[ d ] += distance; }

	@Override
	public void move( final long distance, final int d ) { position[ d ] += distance; }

	@Override
	public void move( final Localizable localizable )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += localizable.getIntPosition( d );
	}

	@Override
	public void move( final int[] distance )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += distance[ d ];
	}

	@Override
	public void move( final long[] distance )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] += distance[ d ];
	}

	@Override
	public void setPosition( final Localizable localizable )
	{
		localizable.localize( position );
	}

	@Override
	public void setPosition( final int[] pos )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = pos[ d ];
	}

	@Override
	public void setPosition( final long[] pos )
	{
		for ( int d = 0; d < n; ++d )
			position[ d ] = ( int ) pos[ d ];
	}

	@Override
	public void setPosition( final int pos, final int d )
	{
		position[ d ] = pos;
	}

	@Override
	public void setPosition( final long pos, final int d )
	{
		position[ d ] = ( int ) pos;
	}

}
