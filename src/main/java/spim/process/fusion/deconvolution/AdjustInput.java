package spim.process.fusion.deconvolution;

import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import spim.Threads;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.type.numeric.real.FloatType;
import mpicbg.spim.io.IOFunctions;
import mpicbg.util.RealSum;

public class AdjustInput
{
	public static Random rnd = new Random( 14235235 );

	/**
	 * Norms an image so that the sum over all pixels is 1.
	 * 
	 * @param img - the Image to normalize
	 */
	final public static void normImg( final IterableInterval< FloatType > img )
	{
		final double sum = sumImg( img );

		for ( final FloatType t : img )
			t.set( (float) ((double)t.get() / sum) );
	}
	
	/**
	 * @param img - the input Image
	 * @return - the sum of all pixels using {@link RealSum}
	 */
	final public static double sumImg( final IterableInterval< FloatType > img )
	{
		final int numPortions = Threads.numThreads() * 2;

		final RealSum[] sums = new RealSum[ numPortions ];
		final AtomicInteger ai = new AtomicInteger( 0 );

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( img.size(), numPortions );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >() 
			{
				@Override
				public Void call() throws Exception
				{
					final int id = ai.getAndIncrement();

					final RealSum sum = new RealSum();

					final Cursor< FloatType > c = img.cursor();
					c.jumpFwd( portion.getStartPosition() );

					for ( long j = 0; j < portion.getLoopSize(); ++j )
						sum.add( c.next().get() );

					sums[ id ] = sum;

					return null;
				}
			});
		}

		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			IOFunctions.println( "Failed to compute sumImage: " + e );
			e.printStackTrace();
			return Double.NaN;
		}

		taskExecutor.shutdown();

		final RealSum sum = new RealSum();
		sum.add( sums[ 0 ].getSum() );
		
		for ( final RealSum s : sums )
			sum.add( s.getSum() );

		return sum.getSum();
	}
}
