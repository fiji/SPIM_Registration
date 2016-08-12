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
public class ProcessVirtualPortionWeight< T extends RealType< T > >  extends ProcessVirtualPortion< T >
{
	final ArrayList< RandomAccessibleInterval< FloatType > > weights;

	public ProcessVirtualPortionWeight(
			final ImagePortion portion,
			final ArrayList< RandomAccessibleInterval< FloatType > > imgs,
			final ArrayList< RandomAccessibleInterval< FloatType > > weights,
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
		final ArrayList< RandomAccess< FloatType > > weightRAs = new ArrayList< RandomAccess< FloatType > >( numViews );

		for ( int i = 0; i < numViews; ++i )
		{
			inputRAs.add( imgs.get( i ).randomAccess() );
			weightRAs.add( weights.get( i ).randomAccess() );
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
					final RandomAccess< FloatType > w = weightRAs.get( i );
					w.setPosition( cursor );

					final float weight = w.get().get();

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
