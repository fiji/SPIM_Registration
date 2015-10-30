package spim.process.fusion.deconvolution;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class InputRandomAccessible< T extends RealType< T > > implements RandomAccessible< FloatType >
{
	final RandomAccessibleInterval< T > img;
	final AffineTransform3D transform;
	final long[] offset;

	public InputRandomAccessible(
		final RandomAccessibleInterval< T > img, // from ImgLoader
		final AffineTransform3D transform,
		final long[] offset )
	{
		this.img = img;
		this.transform = transform;
		this.offset = offset;
	}

	@Override
	public RandomAccess< FloatType > randomAccess()
	{
		return new InputRandomAccess< T >( img, transform, offset );
	}

	@Override
	public RandomAccess< FloatType > randomAccess( final Interval arg0 )
	{
		return randomAccess();
	}

	@Override
	public int numDimensions()
	{
		return img.numDimensions();
	}
}
