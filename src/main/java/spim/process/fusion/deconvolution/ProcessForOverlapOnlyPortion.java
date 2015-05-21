package spim.process.fusion.deconvolution;

import java.util.concurrent.Callable;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccess;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.weights.Blending;

/**
 * Fuse one portion of a paralell fusion, supports no weights
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ProcessForOverlapOnlyPortion implements Callable< String >
{
	final ImagePortion portion;
	final Interval imgInterval;
	final AffineTransform3D transform;
	final Img< FloatType > blendingImg, overlapImg;
	final BoundingBoxGUI bb;
	final Blending blending;
	
	public ProcessForOverlapOnlyPortion(
			final ImagePortion portion,
			final Interval imgInterval,
			final Blending blending,
			final AffineTransform3D transform,
			final Img< FloatType > overlapImg,
			final Img< FloatType > blendingImg,
			final BoundingBoxGUI bb )
	{
		this.portion = portion;
		this.imgInterval = imgInterval;
		this.blendingImg = blendingImg;
		this.transform = transform;
		this.overlapImg = overlapImg;
		this.blending = blending;
		this.bb = bb;
	}
	
	@Override
	public String call() throws Exception 
	{
		// make the blending and get the transformations
		final RealRandomAccess< FloatType > wr = blending.realRandomAccess();
		
		final int[] imgSize = new int[]{ (int)imgInterval.dimension( 0 ), (int)imgInterval.dimension( 1 ), (int)imgInterval.dimension( 2 ) };

		final Cursor< FloatType > cursorO = overlapImg.localizingCursor();
		final Cursor< FloatType > cursorB = blendingImg.cursor();

		final float[] s = new float[ 3 ];
		final float[] t = new float[ 3 ];
		
		cursorO.jumpFwd( portion.getStartPosition() );
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
		
		return portion + " finished successfully (weights only).";
	}
}
