package spim.process.fusion.deconvolution;

import java.util.concurrent.Callable;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.img.Img;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.weights.Blending;

/**
 * Fuse one portion of a paralell fusion, supports no weights
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class ProcessForDeconvolutionPortion implements Callable< String >
{
	final ImagePortion portion;
	final RandomAccessibleInterval< FloatType > img;
	final AffineTransform3D transform;
	final Img< FloatType > weightImg, fusedImg;
	final BoundingBoxGUI bb;
	final Blending blending;
	
	public ProcessForDeconvolutionPortion(
			final ImagePortion portion,
			final RandomAccessibleInterval< FloatType > img,
			final Blending blending,
			final AffineTransform3D transform,
			final Img< FloatType > fusedImg,
			final Img< FloatType > weightImg,
			final BoundingBoxGUI bb )
	{
		this.portion = portion;
		this.img = img;
		this.weightImg = weightImg;
		this.transform = transform;
		this.fusedImg = fusedImg;
		this.blending = blending;
		this.bb = bb;
	}
	
	@Override
	public String call() throws Exception 
	{
		final NLinearInterpolatorFactory< FloatType > f = new NLinearInterpolatorFactory< FloatType >();
		
		// make the interpolators and get the transformations
		final RealRandomAccess< FloatType > ir = Views.interpolate( Views.extendMirrorSingle( img ), f ).realRandomAccess();
		final RealRandomAccess< FloatType > wr = blending.realRandomAccess();
		
		final int[] imgSize = new int[]{ (int)img.dimension( 0 ), (int)img.dimension( 1 ), (int)img.dimension( 2 ) };

		final Cursor< FloatType > cursor = fusedImg.localizingCursor();
		final Cursor< FloatType > cursorW = weightImg.cursor();

		final float[] s = new float[ 3 ];
		final float[] t = new float[ 3 ];
		
		cursor.jumpFwd( portion.getStartPosition() );
		cursorW.jumpFwd( portion.getStartPosition() );
		
		for ( int j = 0; j < portion.getLoopSize(); ++j )
		{
			// move img cursor forward any get the value (saves one access)
			final FloatType v = cursor.next();
			cursor.localize( s );
			
			// move weight cursor forward and get the value 
			final FloatType w = cursorW.next();

			s[ 0 ] += bb.min( 0 );
			s[ 1 ] += bb.min( 1 );
			s[ 2 ] += bb.min( 2 );
			
			transform.applyInverse( t, s );
			
			if ( FusionHelper.intersects( t[ 0 ], t[ 1 ], t[ 2 ], imgSize[ 0 ], imgSize[ 1 ], imgSize[ 2 ] ) )
			{
				ir.setPosition( t );
				wr.setPosition( t );
				
				v.set( ir.get() );
				w.set( wr.get() );
			}
		}
		
		return portion + " finished successfully (individual fusion, no weights).";
	}
}
