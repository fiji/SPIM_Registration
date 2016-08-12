package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.concurrent.Callable;

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
public class ProcessVirtualPortion< T extends RealType< T > > implements Callable< String >
{
	final ImagePortion portion;
	final ArrayList< RandomAccessibleInterval< FloatType > > imgs;
	final Img< T > fusedImg;
	
	public ProcessVirtualPortion(
			final ImagePortion portion,
			final ArrayList< RandomAccessibleInterval< FloatType > > imgs,
			final Img< T > fusedImg )
	{
		this.portion = portion;
		this.imgs = imgs;
		this.fusedImg = fusedImg;
	}

	@Override
	public String call() throws Exception 
	{
		final int numViews = imgs.size();

		final ArrayList< RandomAccess< FloatType > > inputRAs = new ArrayList< RandomAccess< FloatType > >( numViews );

		for ( int i = 0; i < numViews; ++i )
			inputRAs.add( imgs.get( i ).randomAccess() );

		final Cursor< T > cursor = fusedImg.localizingCursor();
		cursor.jumpFwd( portion.getStartPosition() );

		for ( int j = 0; j < portion.getLoopSize(); ++j )
		{
			final T v = cursor.next();

			double sum = 0;
			int sumW = 0;

			for ( int i = 0; i < numViews; ++i )
			{
				final RandomAccess< FloatType > r = inputRAs.get( i );
				r.setPosition( cursor );

				final float input = r.get().get();

				if ( input >= 0 )
				{
					sum += input;
					++sumW;
				}
			}

			if ( sumW > 0 )
				v.setReal( sum / sumW );
		}
		
		return portion + " finished successfully (no weights).";
	}
}
