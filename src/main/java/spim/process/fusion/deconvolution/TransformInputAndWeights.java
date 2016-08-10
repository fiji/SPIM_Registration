package spim.process.fusion.deconvolution;

import java.util.concurrent.Callable;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.weights.Blending;

/**
 * Fuse one portion of a paralell fusion, supports no weights
 *
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class TransformInputAndWeights implements Callable< String >
{
	final ImagePortion portion;
	final RandomAccessibleInterval< FloatType > img;
	final AffineTransform3D transform;
	final RandomAccessibleInterval< FloatType > weightImg, transformedImg;
	final Blending blending;
	final int offsetX, offsetY, offsetZ, imgSizeX, imgSizeY, imgSizeZ;

	public TransformInputAndWeights(
			final ImagePortion portion,
			final RandomAccessibleInterval< FloatType > img,
			final Blending blending,
			final AffineTransform3D transform,
			final RandomAccessibleInterval< FloatType > transformedImg,
			final RandomAccessibleInterval< FloatType > weightImg,
			final long[] offset )
	{
		this.portion = portion;
		this.img = img;
		this.weightImg = weightImg;
		this.transform = transform;
		this.transformedImg = transformedImg;
		this.blending = blending;

		this.offsetX = (int)offset[ 0 ];
		this.offsetY = (int)offset[ 1 ];
		this.offsetZ = (int)offset[ 2 ];

		this.imgSizeX = (int)img.dimension( 0 );
		this.imgSizeY = (int)img.dimension( 1 );
		this.imgSizeZ = (int)img.dimension( 2 );
	}
	
	@Override
	public String call() throws Exception 
	{
		final NLinearInterpolatorFactory< FloatType > f = new NLinearInterpolatorFactory< FloatType >();
		
		// make the interpolators and get the transformations
		final RealRandomAccess< FloatType > ir = Views.interpolate( Views.extendMirrorSingle( img ), f ).realRandomAccess();
		final RealRandomAccess< FloatType > wr = blending.realRandomAccess();

		final Cursor< FloatType > cursor = Views.iterable( transformedImg ).localizingCursor();
		final Cursor< FloatType > cursorW = Views.iterable( weightImg ).cursor();

		final float[] s = new float[ 3 ];
		final float[] t = new float[ 3 ];

		cursor.jumpFwd( portion.getStartPosition() );
		cursorW.jumpFwd( portion.getStartPosition() );

		for ( int j = 0; j < portion.getLoopSize(); ++j )
			loop( cursor, cursorW, ir, wr, transform, s, t, offsetX, offsetY, offsetZ, imgSizeX, imgSizeY, imgSizeZ );

		return portion + " finished successfully (transform input & precompute weights).";
	}

	private static final void loop(
			final Cursor< FloatType > cursor,
			final Cursor< FloatType > cursorW,
			final RealRandomAccess< FloatType > ir,
			final RealRandomAccess< FloatType > wr,
			final AffineTransform3D transform,
			final float[] s, final float[] t,
			final int offsetX, final int offsetY, final int offsetZ,
			final int imgSizeX, final int imgSizeY, final int imgSizeZ )
	{
		// move img cursor forward any get the value (saves one access)
		final FloatType v = cursor.next();
		cursor.localize( s );

		// move weight cursor forward and get the value 
		final FloatType w = cursorW.next();

		s[ 0 ] += offsetX;
		s[ 1 ] += offsetY;
		s[ 2 ] += offsetZ;
		
		transform.applyInverse( t, s );
		
		if ( FusionHelper.intersects( t[ 0 ], t[ 1 ], t[ 2 ], imgSizeX, imgSizeY, imgSizeZ ) )
		{
			ir.setPosition( t );

			// do not accept 0 values in the data where image data is present, 0 means no image data is available
			// (used in MVDeconvolution.computeQuotient)
			v.set( Math.max( MVDeconvolution.minValue, ir.get().get() ) );
		}

		// compute weights in any case (the border can be negative!)
		wr.setPosition( t );
		w.set( wr.get() );
	}
}
