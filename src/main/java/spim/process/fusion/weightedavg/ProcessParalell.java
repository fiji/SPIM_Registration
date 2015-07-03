package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.Threads;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.headless.fusion.FusionTools;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;

public class ProcessParalell
{
	/** 
	 * Fuses one stack
	 */
	public static < T extends RealType< T > & NativeType< T > > void fuse(
			final T type,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final RandomAccessibleInterval< T > output,
			final List< RandomAccessibleInterval< T > > input,
			final List< ? extends List< RealRandomAccessible< FloatType > > > weights,
			final List< AffineTransform3D > models,
			final BoundingBox bb,
			final int downsampling,
			final ExecutorService exec )
	{
		if ( !FusionTools.matches( output, bb, downsampling ) )
			throw new RuntimeException( "Output RAI does not match BoundingBox with downsampling" );

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( Views.iterable( output ).size(), Threads.numThreads() * 4 );

		// set up executor service
		final ExecutorService taskExecutor;

		if ( exec == null )
			taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		else
			taskExecutor = exec;

		final ArrayList< ProcessParalellPortion< T > > tasks = new ArrayList< ProcessParalellPortion< T > >();

		if ( weights.get( 0 ).size() == 0 ) // no weights
		{
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessParalellPortion< T >( portion, input, interpolatorFactory, models, output, bb, downsampling ) );
		}
		else if ( weights.get( 0 ).size() > 1 ) // many weights
		{
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessParalellPortionWeights< T >( portion, input, weights, interpolatorFactory, models, output, bb, downsampling ) );
		}
		else // one weight
		{
			final ArrayList< RealRandomAccessible< FloatType > > singleWeight = new ArrayList< RealRandomAccessible< FloatType > >();
			
			for ( int i = 0; i < input.size(); ++i )
				singleWeight.add( weights.get( i ).get( 0 ) );
			
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessParalellPortionWeight< T >( portion, input, singleWeight, interpolatorFactory, models, output, bb, downsampling ) );
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Starting fusion process.");

		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Failed to compute fusion: " + e );
			e.printStackTrace();
			return;
		}

		if ( exec == null )
			taskExecutor.shutdown();
		
		return;
	}
}
