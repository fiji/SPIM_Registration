package spim.process.fusion.deconvolution;

import java.util.concurrent.Callable;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.weights.Blending;

/**
 * Fuse one portion of a paralell fusion, supports no weights
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 * @param <T>
 */
public class TransformWeights implements Callable< String >
{
	final ImagePortion portion;
	final AffineTransform3D transform;
	final RandomAccessibleInterval< FloatType > weightImg;
	final Blending blending;
	final int offsetX, offsetY, offsetZ, imgSizeX, imgSizeY, imgSizeZ;

	public TransformWeights(
			final ImagePortion portion,
			final Blending blending,
			final AffineTransform3D transform,
			final RandomAccessibleInterval< FloatType > weightImg,
			final long[] offset )
	{
		this.portion = portion;
		this.weightImg = weightImg;
		this.transform = transform;
		this.blending = blending;

		this.offsetX = (int)offset[ 0 ];
		this.offsetY = (int)offset[ 1 ];
		this.offsetZ = (int)offset[ 2 ];

		this.imgSizeX = (int)weightImg.dimension( 0 );
		this.imgSizeY = (int)weightImg.dimension( 1 );
		this.imgSizeZ = (int)weightImg.dimension( 2 );
	}
	
	@Override
	public String call() throws Exception 
	{
		// make the interpolators and get the transformations
		final RealRandomAccess< FloatType > wr = blending.realRandomAccess();

		final Cursor< FloatType > cursorW = Views.iterable( weightImg ).cursor();

		final float[] s = new float[ 3 ];
		final float[] t = new float[ 3 ];

		cursorW.jumpFwd( portion.getStartPosition() );

		for ( int j = 0; j < portion.getLoopSize(); ++j )
			loop( cursorW, wr, transform, s, t, offsetX, offsetY, offsetZ, imgSizeX, imgSizeY, imgSizeZ );

		return portion + " finished successfully (transform input & precompute weights).";
	}

	private static final void loop(
			final Cursor< FloatType > cursorW,
			final RealRandomAccess< FloatType > wr,
			final AffineTransform3D transform,
			final float[] s, final float[] t,
			final int offsetX, final int offsetY, final int offsetZ,
			final int imgSizeX, final int imgSizeY, final int imgSizeZ )
	{
		// move weight cursor forward and get the value 
		final FloatType w = cursorW.next();
		cursorW.localize( s );

		s[ 0 ] += offsetX;
		s[ 1 ] += offsetY;
		s[ 2 ] += offsetZ;
		
		transform.applyInverse( t, s );

		// compute weights in any case (the border can be negative!)
		wr.setPosition( t );
		w.set( wr.get() );
	}
}
