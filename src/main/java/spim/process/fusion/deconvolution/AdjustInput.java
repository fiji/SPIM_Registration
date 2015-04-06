package spim.process.fusion.deconvolution;

import ij.IJ;

import java.util.ArrayList;
import java.util.Date;
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
import net.imglib2.view.Views;
import mpicbg.spim.io.IOFunctions;
import mpicbg.util.RealSum;

public class AdjustInput
{
	public static Random rnd = new Random( 14235235 );

	/**
	 * Norms an image so that the sum over all pixels is 1.
	 * 
	 * @param img - the {@link Image} to normalize
	 */
	final public static void normImage( final IterableInterval< FloatType > img )
	{
		final double sum = sumImage( img );

		for ( final FloatType t : img )
			t.set( (float) ((double)t.get() / sum) );
	}
	
	/**
	 * @param img - the input {@link Image}
	 * @return - the sum of all pixels using {@link RealSum}
	 */
	final public static double sumImage( final IterableInterval< FloatType > img )
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

	public static double[] normAllImages( final ArrayList< MVDeconFFT > data )
	{
		final int nThreads = Threads.numThreads();
		final int nPortions = nThreads * 2;

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( Views.iterable( data.get( 0 ).getImage() ).size(), nPortions );

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": numThreads = " + nThreads );

		final int[] minNumOverlap = new int[ nPortions ];
		final long[] avgNumOverlap = new long[ nPortions ];
		final int[] countAvgNumOverlap = new int[ nPortions ];
		
		final RealSum[] sum = new RealSum[ nPortions ];
		final long[] count = new long[ nPortions ];

		final AtomicInteger ai = new AtomicInteger( 0 );

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

					final long start = portion.getStartPosition();
					final long loopSize = portion.getLoopSize();

					final RealSum mySum = new RealSum();
					int myCount = 0;
					int myMinNumOverlap = Integer.MAX_VALUE;
					long myAvgNumOverlap = 0;
					int myCountAvgNumOverlap = 0;

					final ArrayList<Cursor<FloatType>> cursorsImage = new ArrayList<Cursor<FloatType>>();
					final ArrayList<Cursor<FloatType>> cursorsWeight = new ArrayList<Cursor<FloatType>>();

					for ( final MVDeconFFT fft : data )
					{
						cursorsImage.add( Views.iterable( fft.getImage() ).cursor() );
						if ( fft.getWeight() != null )
							cursorsWeight.add( Views.iterable( fft.getWeight() ).cursor() );
					}

					for ( final Cursor<FloatType> c : cursorsImage )
						c.jumpFwd( start );

					for ( final Cursor<FloatType> c : cursorsWeight )
						c.jumpFwd( start );

					for ( long l = 0; l < loopSize; ++l )
					{
						for ( final Cursor<FloatType> c : cursorsImage )
							c.fwd();

						for ( final Cursor<FloatType> c : cursorsWeight )
							c.fwd();

						// sum up individual intensities
						double sumLocal = 0;
						int countLocal = 0;

						for ( int i = 0; i < cursorsImage.size(); ++i )
						{
							if ( cursorsWeight.get( i ).get().get() != 0 )
							{
								sumLocal += cursorsImage.get( i ).get().get();
								countLocal++;
							}
						}

						// at least two overlap to compute the average intensity there
						if ( countLocal > 1 )
						{
							mySum.add( sumLocal );
							myCount += countLocal;
						}

						if ( countLocal > 0 )
						{
							myAvgNumOverlap += countLocal;
							myCountAvgNumOverlap++;

							myMinNumOverlap = Math.min( countLocal, myMinNumOverlap );
						}
					}

					sum[ id ] = mySum;
					count[ id ] = myCount;
					minNumOverlap[ id ] = myMinNumOverlap;
					avgNumOverlap[ id ] = myAvgNumOverlap;
					countAvgNumOverlap[ id ] = myCountAvgNumOverlap;

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
			IOFunctions.println( "Failed to compute normalization for all images: " + e );
			e.printStackTrace();
			return null;
		}

		taskExecutor.shutdown();

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": done normalizing." );

		int minNumOverlapResult = minNumOverlap[ 0 ];
		long avgNumOverlapResult = avgNumOverlap[ 0 ];
		int countAvgNumOverlapResult = countAvgNumOverlap[ 0 ];

		RealSum sumResult = new RealSum();
		sumResult.add( sum[ 0 ].getSum() );
		long countResult = count[ 0 ];

		for ( int i = 1; i < nPortions; ++i )
		{
			minNumOverlapResult = Math.min( minNumOverlapResult, minNumOverlap[ i ] );
			avgNumOverlapResult += avgNumOverlap[ i ];
			countAvgNumOverlapResult += countAvgNumOverlap[ i ];
			countResult += count[ i ];
			sumResult.add( sum[ i ].getSum() );
		}

		double avgNumOverlapFinal = (avgNumOverlapResult/(double)countAvgNumOverlapResult);
		IJ.log( "Min number of overlapping views: " + minNumOverlapResult );
		IJ.log( "Average number of overlapping views: " + avgNumOverlapFinal );

		if ( countResult == 0 )
			return new double[]{ 1, minNumOverlapResult, avgNumOverlapFinal };

		// compute the average sum
		final double avg = sumResult.getSum() / (double)countResult;

		// return the average intensity in the overlapping area
		return new double[]{ avg, minNumOverlapResult, avgNumOverlapFinal };
	}

}
