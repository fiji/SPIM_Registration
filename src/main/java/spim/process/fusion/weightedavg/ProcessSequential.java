package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.Threads;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;

public class ProcessSequential
{
	public static < T extends RealType< T > & NativeType< T > > Pair< Img< T >, Img< FloatType > > createSequentialFusionImages(
			final T type,
			final ImgFactory< T > factory,
			final BoundingBox bb,
			final int downsampling )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused & weight image.");

		// try creating the output (type needs to be there to define T)
		final Img< T > sumOutput = factory.create( bb.getDimensions( downsampling ), type );
		try
		{
			final Img< FloatType > sumWeights = factory.imgFactory( new FloatType() ).create( bb.getDimensions( downsampling ), new FloatType() );

			return new ValuePair< Img< T >, Img< FloatType > >( sumOutput, sumWeights );
		}
		catch (IncompatibleTypeException e)
		{
			e.printStackTrace();
			throw new RuntimeException( e );
		}
	}

	/** 
	 * Fuses one stack sequentially
	 */
	public static < T extends RealType< T > & NativeType< T > > void addView(
			final T type,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final RandomAccessibleInterval< T > sumOutput,
			final RandomAccessibleInterval< FloatType > sumWeight,
			final RandomAccessibleInterval< T > input,
			final List< RealRandomAccessible< FloatType > > weights,
			final AffineTransform3D model,
			final BoundingBox bb,
			final int downsampling,
			final ExecutorService exec )
	{
		//IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusing view " + start + " ... " + (end-1) + " of " + (allInputData.size()-1) );

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions =
				FusionHelper.divideIntoPortions( Views.iterable( sumOutput ).size(), Threads.numThreads() * 4 );

		// set up executor service
		final ExecutorService taskExecutor;

		if ( exec == null )
			taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		else
			taskExecutor = exec;
		final ArrayList< ProcessSequentialPortion< T > > tasks = new ArrayList< ProcessSequentialPortion< T > >();

		if ( weights.size() == 0 ) // no weights
		{		
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessSequentialPortion< T >(
						portion, input, interpolatorFactory, model, sumOutput, sumWeight, bb, downsampling ) );
		}
		else if ( weights.size() > 1 ) // many weights
		{
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessSequentialPortionWeights< T >(
						portion, input, weights, interpolatorFactory, model, sumOutput, sumWeight, bb, downsampling ) );
		}
		else // one weight
		{
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessSequentialPortionWeight< T >(
						portion, input, weights.get( 0 ), interpolatorFactory, model, sumOutput, sumWeight, bb, downsampling ) );
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
	}

	/**
	 * in-place in img
	 * 
	 * @param img
	 * @param weights
	 */
	public static < T extends RealType< T > > void mergeSequentialFusionImage( final Img< T > img, final Img< FloatType > weights )
	{
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( img.size(), Threads.numThreads() * 4 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< String > > tasks = new ArrayList< Callable< String > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< String >() 
					{
						@Override
						public String call() throws Exception
						{
							final Cursor< T > cursor = img.cursor();
							final Cursor< FloatType > cursorW = weights.cursor();
							
							cursor.jumpFwd( portion.getStartPosition() );
							cursorW.jumpFwd( portion.getStartPosition() );
							
							for ( int j = 0; j < portion.getLoopSize(); ++j )
							{
								final float w = cursorW.next().get();
								final T type = cursor.next();

								if ( w > 0 )
									type.setReal( type.getRealFloat() / w );
							}
							
							return "";
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
			IOFunctions.println( "Failed to merge final image: " + e );
			e.printStackTrace();
			return;
		}

		taskExecutor.shutdown();
		
	}
}
