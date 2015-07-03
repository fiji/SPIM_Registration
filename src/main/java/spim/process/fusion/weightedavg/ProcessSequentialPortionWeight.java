package spim.process.fusion.weightedavg;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;

/**
 * Fuse one portion of a sequential fusion, supports one weight function
 * This is basically identical to the parallel fusion except it does store the weights
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 * @param <T>
 */
public class ProcessSequentialPortionWeight< T extends RealType< T > > extends ProcessSequentialPortion< T >
{
	final RealRandomAccessible< FloatType > weight;

	public ProcessSequentialPortionWeight(
			final ImagePortion portion,
			final RandomAccessibleInterval< T > input,
			final RealRandomAccessible< FloatType > weight,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final AffineTransform3D transform,
			final RandomAccessibleInterval< T > sumOutput,
			final RandomAccessibleInterval< FloatType > sumWeight,
			final BoundingBox bb,
			final int downsampling )
	{
		super( portion, input, interpolatorFactory, transform, sumOutput, sumWeight, bb, downsampling );
		
		this.weight = weight;
	}

	@Override
	public String call() throws Exception 
	{
		// make the interpolators, weights and get the transformations
		final RealRandomAccess< T > r = Views.interpolate( Views.extendMirrorSingle( input ), interpolatorFactory ).realRandomAccess();
		final RealRandomAccess< FloatType > wr = weight.realRandomAccess();
		final int[] imgSize = new int[]{ (int)input.dimension( 0 ), (int)input.dimension( 1 ), (int)input.dimension( 2 ) };

		final Cursor< T > cursor = Views.iterable( sumOutput ).localizingCursor();
		final Cursor< FloatType > cursorW = Views.iterable( sumWeight ).cursor();

		final float[] s = new float[ 3 ];
		final float[] t = new float[ 3 ];
		
		cursor.jumpFwd( portion.getStartPosition() );
		cursorW.jumpFwd( portion.getStartPosition() );
		
		for ( int j = 0; j < portion.getLoopSize(); ++j )
		{
			// move img cursor forward any get the value (saves one access)
			final T v = cursor.next();
			cursor.localize( s );

			// move weight cursor forward and get the value 
			final FloatType w = cursorW.next();

			if ( doDownSampling )
			{
				s[ 0 ] *= downSampling;
				s[ 1 ] *= downSampling;
				s[ 2 ] *= downSampling;
			}

			s[ 0 ] += bb.min( 0 );
			s[ 1 ] += bb.min( 1 );
			s[ 2 ] += bb.min( 2 );

			transform.applyInverse( t, s );

			if ( FusionHelper.intersects( t[ 0 ], t[ 1 ], t[ 2 ], imgSize[ 0 ], imgSize[ 1 ], imgSize[ 2 ] ) )
			{
				r.setPosition( t );
				wr.setPosition( t );

				final float w1 = wr.get().get();

				v.setReal( v.getRealFloat() + r.get().getRealDouble() * w1 );
				w.set( w.get() + w1 );
			}
		}

		return portion + " finished successfully (one weight).";
	}

}
