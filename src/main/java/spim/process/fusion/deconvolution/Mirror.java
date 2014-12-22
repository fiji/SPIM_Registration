package spim.process.fusion.deconvolution;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import spim.Threads;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;

import net.imglib2.img.Img;

/**
 * Mirrors an n-dimensional image along an axis (one of the dimensions).
 * The calculation is performed in-place and multithreaded.
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class Mirror
{
	/**
	 * @param image - The {@link Image} to mirror
	 * @param dimension - The axis to mirror (e.g. 0->x-Axis->horizontally, 1->y-axis->vertically)
	 * @param numThreads - number of threads
	 */
	public static < T > boolean mirror( final Img< T > image, final int dimension, final int numThreads )
	{
		final int n = image.numDimensions();

		// divide the image into chunks
		final long imageSize = image.size();
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( imageSize, numThreads * 4 );

		final long maxMirror = image.dimension( dimension ) - 1;
		final long sizeMirrorH = image.dimension( dimension ) / 2;

		final AtomicInteger ai = new AtomicInteger( 0 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		for ( fina    l ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >() 
			{
				@Override
				public Void call() throws Exception
				{
					final LocalizableCursor<T> cursorIn = image.createLocalizableCursor();
					final LocalizableByDimCursor<T> cursorOut = image.createLocalizableByDimCursor();
					final T temp = image.createType();
					final long[] position = new long[ n ];
					
					// set the cursorIn to right offset
					final long startPosition = myChunk.getStartPosition();
					final long loopSize = myChunk.getLoopSize();
					
					if ( startPosition > 0 )
						cursorIn.fwd( startPosition );
					
					// iterate over all pixels, if they are above the middle switch them with their counterpart
					// from the other half in the respective dimension
					for ( long i = 0; i < loopSize; ++i )
					{
						cursorIn.fwd();
						cursorIn.getPosition( position );
						
						if ( position[ dimension ] <= sizeMirrorH )
						{
							// set the localizable to the correct mirroring position
							position[ dimension ] = maxMirror - position[ dimension ];
							cursorOut.setPosition( position );
							
							// do a triangle switching
							final T in = cursorIn.getType();
							final T out = cursorOut.getType();
							
							temp.set( in );
							in.set( out );
							out.set( temp );
						}
					}
				}
			});
		}
			
		for (int ithread = 0; ithread < threads.length; ++ithread)
			threads[ithread] = new Thread(new Runnable()
			{
				public void run()
				{
					// Thread ID
					final int myNumber = ai.getAndIncrement();
				
					// get chunk of pixels to process
					final Chunk myChunk = threadChunks.get( myNumber );
				
					final LocalizableCursor<T> cursorIn = image.createLocalizableCursor();
					final LocalizableByDimCursor<T> cursorOut = image.createLocalizableByDimCursor();
					final T temp = image.createType();
					final long[] position = new long[ n ];
					
					// set the cursorIn to right offset
					final long startPosition = myChunk.getStartPosition();
					final long loopSize = myChunk.getLoopSize();
					
					if ( startPosition > 0 )
						cursorIn.fwd( startPosition );
					
					// iterate over all pixels, if they are above the middle switch them with their counterpart
					// from the other half in the respective dimension
					for ( long i = 0; i < loopSize; ++i )
					{
						cursorIn.fwd();
						cursorIn.getPosition( position );
						
						if ( position[ dimension ] <= sizeMirrorH )
						{
							// set the localizable to the correct mirroring position
							position[ dimension ] = maxMirror - position[ dimension ];
							cursorOut.setPosition( position );
							
							// do a triangle switching
							final T in = cursorIn.getType();
							final T out = cursorOut.getType();
							
							temp.set( in );
							in.set( out );
							out.set( temp );
						}
					}
		
                }
            });
        
        SimpleMultiThreading.startAndJoin(threads);
	}
}
