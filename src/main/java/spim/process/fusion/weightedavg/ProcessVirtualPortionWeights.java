package spim.process.fusion.weightedavg;

import java.util.ArrayList;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import spim.process.fusion.ImagePortion;

/**
 * Fuse one portion of a paralell fusion, supports no weights
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 * @param <T>
 */
public class ProcessVirtualPortionWeights< T extends RealType< T > > extends ProcessVirtualPortion< T >
{
	final ArrayList< ArrayList< RandomAccessibleInterval< FloatType > > > weights;

	public ProcessVirtualPortionWeights(
			final ImagePortion portion,
			final ArrayList< RandomAccessibleInterval< FloatType > > imgs,
			final ArrayList< ArrayList< RandomAccessibleInterval< FloatType > > > weights,
			final Img< T > fusedImg )
	{
		super( portion, imgs, fusedImg );

		this.weights = weights;
	}

	@Override
	public String call() throws Exception 
	{
		final int numViews = imgs.size();

		final ArrayList< RandomAccess< FloatType > > inputRAs = new ArrayList< RandomAccess< FloatType > >( numViews );
		final ArrayList< ArrayList< RandomAccess< FloatType > > > weightRAs = new ArrayList< ArrayList< RandomAccess< FloatType > > >( numViews );

		for ( int i = 0; i < numViews; ++i )
		{
			inputRAs.add( imgs.get( i ).randomAccess() );
			
			final ArrayList< RandomAccess< FloatType > > lw = new ArrayList< RandomAccess< FloatType > >();

			for ( final RandomAccessibleInterval< FloatType > rai : weights.get( i ) )
				lw.add( rai.randomAccess() );

			weightRAs.add( lw );
		}

		final Cursor< T > cursor = fusedImg.localizingCursor();
		cursor.jumpFwd( portion.getStartPosition() );

		for ( int j = 0; j < portion.getLoopSize(); ++j )
		{
			final T v = cursor.next();

			double sum = 0;
			double sumW = 0;

			for ( int i = 0; i < numViews; ++i )
			{
				final RandomAccess< FloatType > r = inputRAs.get( i );
				r.setPosition( cursor );

				final float input = r.get().get();

				if ( input >= 0 )
				{
					double weight = 1;
					
					for ( final RandomAccess< FloatType > raw : weightRAs.get( i ) )
					{
						raw.setPosition( cursor );
						weight *= raw.get().get();
					}

					sum += input * weight;
					sumW += weight;
				}
			}

			if ( sumW > 0 )
				v.setReal( sum / sumW );
		}
		
		return portion + " finished successfully (one weight).";
	}
}
