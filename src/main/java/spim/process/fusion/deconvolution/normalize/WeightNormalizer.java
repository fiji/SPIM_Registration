package spim.process.fusion.deconvolution.normalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.Threads;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;

public abstract class WeightNormalizer
{
	// for additional smoothing of weights in areas where many views contribute less than 100%
	public static float maxDiffRange = 0.1f;
	public static float scalingRange = 0.05f;
	public static boolean additionalSmoothBlending = false;

	int minOverlappingViews;
	double avgOverlappingViews;

	final List< RandomAccessibleInterval< FloatType > > weights;

	public WeightNormalizer( final List< RandomAccessibleInterval< FloatType > > weights )
	{
		this.weights = weights;
	}

	/**
	 * Computes the weight-adjustment and return new double[]{ minNumViews, avgNumViews }
	 * 
	 * @param portion
	 * @param additionalSmoothBlending
	 * @param maxDiffRange
	 * @param scalingRange
	 * @return
	 */
	protected abstract Callable< double[] > createWeightAdjustmentThread( final ImagePortion portion, final boolean additionalSmoothBlending, final float maxDiffRange, final float scalingRange );

	/**
	 * Called after createWeightAdjustmentThread
	 */
	protected abstract void postProcess();

	/**
	 * Modify weights for OSEM factor
	 */
	public abstract void adjustForOSEM( final double osemspeedup );

	public int getMinOverlappingViews() { return minOverlappingViews; }
	public double getAvgOverlappingViews() { return avgOverlappingViews; }

	public boolean process()
	{
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( Views.iterable( weights.get( 0 ) ).size(), Threads.numThreads() * 2 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< double[] > > tasks = new ArrayList< Callable< double[] > >();

		for ( final ImagePortion portion : portions )
			tasks.add( createWeightAdjustmentThread( portion, additionalSmoothBlending, maxDiffRange, scalingRange ) );

		// run threads
		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< double[] > > futures = taskExecutor.invokeAll( tasks );
			
			this.minOverlappingViews = weights.size();
			this.avgOverlappingViews = 0;
			
			for ( final Future< double[] > f : futures )
			{
				final double[] minAvg = f.get();
				
				this.minOverlappingViews = Math.min( this.minOverlappingViews, (int)Math.round( minAvg[ 0 ] ) );
				this.avgOverlappingViews += minAvg[ 1 ];
			}
			
			this.avgOverlappingViews /= futures.size();
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to compute weight normalization for deconvolution: " + e );
			e.printStackTrace();
			return false;
		}

		taskExecutor.shutdown();

		// in case something else is to do afterwards
		postProcess();

		return true;
	}

	final protected static void applySmooth( final ArrayList< Cursor< FloatType > > cursors, double sumW, final float maxDiffRange, final float scalingRange )
	{
		for ( final Cursor< FloatType > c : cursors )
			c.get().set( smoothWeights( c.get().get(), sumW, maxDiffRange, scalingRange ) );
	}

	final public static float smoothWeights( final float w, final double sumW, final float maxDiffRange, final float scalingRange )
	{
		if ( sumW <= 0 )
			return 0;

		final float idealValue = (float)( w / sumW );

		final float diff = w - idealValue;

		// map diff: 0 ... maxDiffRange >> 1 ...  0, rest negative
		final float y = Math.max( 0, ( maxDiffRange - Math.abs( diff ) ) * ( 1.0f / maxDiffRange ) );

		// scale with the value of w
		final float scale = y * w * scalingRange;

		// final function is a scaling down
		return ( Math.min( w, idealValue ) - (float)scale );
	}

	final protected static void applyHard( final ArrayList< Cursor< FloatType > > cursors, final double sumW )
	{
		if ( sumW > 1 )
		{
			for ( final Cursor< FloatType > c : cursors )
				c.get().set( hardWeights( c.get().get(), sumW )  );
		}
	}

	final public static float hardWeights( final float w, final double sumW )
	{
		return (float)( w / sumW );
	}

}
