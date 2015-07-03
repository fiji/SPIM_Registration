package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.List;

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
 * Fuse one portion of a paralell fusion, supports many weight functions
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 * @param <T>
 */
public class ProcessParalellPortionWeights< T extends RealType< T > > extends ProcessParalellPortion< T >
{
	final List< ? extends List< RealRandomAccessible< FloatType > > > weights;
	
	public ProcessParalellPortionWeights(
			final ImagePortion portion,
			final List< RandomAccessibleInterval< T > > imgs,
			final List< ? extends List< RealRandomAccessible< FloatType > > > weights,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final List< AffineTransform3D > transforms,
			final RandomAccessibleInterval< T > fusedImg,
			final BoundingBox bb,
			final int downsampling )
	{
		super( portion, imgs, interpolatorFactory, transforms, fusedImg, bb, downsampling );
		
		this.weights = weights;
	}

	@Override
	public String call() throws Exception 
	{
		final int numViews = imgs.size();
		
		// make the interpolators, weights and get the transformations
		final ArrayList< RealRandomAccess< T > > interpolators = new ArrayList< RealRandomAccess< T > >( numViews );
		final ArrayList< ArrayList< RealRandomAccess< FloatType > > > weightAccess = new ArrayList< ArrayList< RealRandomAccess< FloatType > > >();
		final int[][] imgSizes = new int[ numViews ][ 3 ];
		
		for ( int i = 0; i < numViews; ++i )
		{
			final RandomAccessibleInterval< T > img = imgs.get( i );
			imgSizes[ i ] = new int[]{ (int)img.dimension( 0 ), (int)img.dimension( 1 ), (int)img.dimension( 2 ) };
			
			interpolators.add( Views.interpolate( Views.extendMirrorSingle( img ), interpolatorFactory ).realRandomAccess() );
			
			final ArrayList< RealRandomAccess< FloatType > > list = new ArrayList< RealRandomAccess< FloatType > >();

			for ( final RealRandomAccessible< FloatType > rra : weights.get( i ) )
				list.add( rra.realRandomAccess() );
			
			weightAccess.add( list );
		}

		final Cursor< T > cursor = Views.iterable( fusedImg ).localizingCursor();
		final float[] s = new float[ 3 ];
		final float[] t = new float[ 3 ];
		
		cursor.jumpFwd( portion.getStartPosition() );
		
		for ( int j = 0; j < portion.getLoopSize(); ++j )
		{
			// move img cursor forward any get the value (saves one access)
			final T v = cursor.next();
			cursor.localize( s );
			
			if ( doDownSampling )
			{
				s[ 0 ] *= downSampling;
				s[ 1 ] *= downSampling;
				s[ 2 ] *= downSampling;
			}
			
			s[ 0 ] += bb.min( 0 );
			s[ 1 ] += bb.min( 1 );
			s[ 2 ] += bb.min( 2 );
			
			double sum = 0;
			double sumW = 0;
			
			for ( int i = 0; i < numViews; ++i )
			{				
				transforms[ i ].applyInverse( t, s );
				
				if ( FusionHelper.intersects( t[ 0 ], t[ 1 ], t[ 2 ], imgSizes[ i ][ 0 ], imgSizes[ i ][ 1 ], imgSizes[ i ][ 2 ] ) )
				{
					final RealRandomAccess< T > r = interpolators.get( i );
					r.setPosition( t );
					
					double w = 1;
					
					for ( final RealRandomAccess< FloatType > weight : weightAccess.get( i ) )
					{
						weight.setPosition( t );
						w *= weight.get().get();
					}
					
					sum += r.get().getRealDouble() * w;
					sumW += w;
				}
			}
			
			if ( sumW > 0 )
				v.setReal( sum / sumW );
		}
		
		return portion + " finished successfully (many weights).";
	}

}
