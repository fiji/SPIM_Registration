package spim.process.fusion.transformed;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.ImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.process.fusion.transformed.weights.BlendingRealRandomAccessible;
import spim.process.fusion.transformed.weights.ContentBasedRealRandomAccessible;

public class TransformWeight
{
	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformContentBased(
			final RandomAccessibleInterval< T > inputImg,
			final ImgFactory< ComplexFloatType > imgFactory,
			final double[] sigma1,
			final double[] sigma2,
			final AffineTransform3D transform,
			final Interval boundingBox )
	{
		return transformWeight( new ContentBasedRealRandomAccessible< T >( inputImg, imgFactory, sigma1, sigma2 ), transform, boundingBox );
	}

	public static RandomAccessibleInterval< FloatType > transformBlending(
			final Interval inputImgInterval,
			final float[] border,
			final float[] blending,
			final AffineTransform3D transform,
			final Interval boundingBox )
	{
		return transformWeight( new BlendingRealRandomAccessible( new FinalInterval( inputImgInterval ), border, blending ), transform, boundingBox );
	}

	public static RandomAccessibleInterval< FloatType > transformWeight(
			final RealRandomAccessible< FloatType > rra,
			final AffineTransform3D transform,
			final Interval boundingBox )
	{
		final long[] offset = new long[ rra.numDimensions() ];
		final long[] size = new long[ rra.numDimensions() ];

		for ( int d = 0; d < offset.length; ++d )
		{
			offset[ d ] = boundingBox.min( d );
			size[ d ] = boundingBox.dimension( d );
		}

		// the virtual weight construct
		final RandomAccessible< FloatType > virtualBlending =
				new TransformedRasteredRealRandomAccessible< FloatType >(
					rra,
					new FloatType(),
					transform,
					offset );

		final RandomAccessibleInterval< FloatType > virtualBlendingInterval = Views.interval( virtualBlending, new FinalInterval( size ) );

		return virtualBlendingInterval;
	}
}
