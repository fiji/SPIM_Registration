package spim.process.fusion.deconvolution;

import spim.process.fusion.FusionHelper;
import net.imglib2.AbstractLocalizableInt;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessible;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class InputRandomAccess< T extends RealType< T > > extends AbstractLocalizableInt implements RandomAccess< FloatType >
{
	final RandomAccessibleInterval< T > img;
	final AffineTransform3D transform;
	final long[] offset;
	final float[] s, t;
	final FloatType v;

	final RealRandomAccess< FloatType > ir;
	final int offsetX, offsetY, offsetZ, imgSizeX, imgSizeY, imgSizeZ;

	public InputRandomAccess(
			final RandomAccessibleInterval< T > img, // from ImgLoader
			final AffineTransform3D transform,
			final long[] offset )
	{
		super( img.numDimensions() );

		this.offsetX = (int)offset[ 0 ];
		this.offsetY = (int)offset[ 1 ];
		this.offsetZ = (int)offset[ 2 ];

		this.imgSizeX = (int)img.dimension( 0 );
		this.imgSizeY = (int)img.dimension( 1 );
		this.imgSizeZ = (int)img.dimension( 2 );

		this.img = img;
		this.transform = transform;
		this.offset = offset;
		this.s = new float[ n ];
		this.t = new float[ n ];
		this.v = new FloatType();

		// extend input image and convert to floats
		final ConvertedRandomAccessible< T, FloatType > convertedInput =
				new ConvertedRandomAccessible< T, FloatType >(
						Views.extendMirrorSingle( img ),
						new RealFloatConverter< T >(),
						new FloatType() );

		// make the interpolator
		final NLinearInterpolatorFactory< FloatType > f = new NLinearInterpolatorFactory< FloatType >();
		this.ir = Views.interpolate( convertedInput, f ).realRandomAccess();
	}

	@Override
	public FloatType get()
	{
		// go from PSI(Decon)_image local coordinate system to world coordinate system
		s[ 0 ] += offsetX;
		s[ 1 ] += offsetY;
		s[ 2 ] += offsetZ;

		// go from world coordinate system to local coordinate system of input image (pixel coordinates)
		transform.applyInverse( t, s );

		// check if position t is inside of the input image (pixel coordinates)
		if ( FusionHelper.intersects( t[ 0 ], t[ 1 ], t[ 2 ], imgSizeX, imgSizeY, imgSizeZ ) )
		{
			ir.setPosition( t );

			// do not accept 0 values in the data where image data is present, 0 means no image data is available
			// (used in MVDeconvolution.computeQuotient)
			// here return the minimal value of the lucy-richardson deconvolution = MVDeconvolution.minValue (e.g 0.0001)
			v.set( Math.max( MVDeconvolution.minValue, ir.get().get() ) );
		}
		else
		{
			v.set( 0 );
		}

		return v;
	}

	@Override
	public InputRandomAccess< T > copy()
	{
		return copyRandomAccess();
	}

	@Override
	public InputRandomAccess< T > copyRandomAccess()
	{
		final InputRandomAccess< T > r = new InputRandomAccess< T >( img, transform, offset );
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
