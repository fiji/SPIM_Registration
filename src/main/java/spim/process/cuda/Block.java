package spim.process.cuda;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.iterator.LocalizingZeroMinIntervalIterator;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;

public class Block 
{
	/**
	 * the number of dimensions of this block
	 */
	final int numDimensions;
	
	/**
	 * The dimensions of the block
	 */
	final int[] blockSize;
	
	/**
	 * The offset in coordinates (coordinate system of the original image) 
	 */
	final int[] offset;
	
	/**
	 * The effective size that can be convolved (depends on the kernelsize)
	 */
	final int[] effectiveSize;
	
	/**
	 * The effective offset, i.e. where the useful convolved data starts (coordinate system of the original image)
	 */
	final int[] effectiveOffset;
	
	/**
	 * The effective offset, i.e. where the useful convoved data starts (local coordinate system)
	 */
	final int[] effectiveLocalOffset;

	final Vector< ImagePortion > portions;
	final int numThreads;
	final ExecutorService taskExecutor;

	public Block( final int[] blockSize, final int[] offset, final int[] effectiveSize, final int[] effectiveOffset, final int[] effectiveLocalOffset )
	{
		this.numDimensions = blockSize.length;
		this.blockSize = blockSize.clone();
		this.offset = offset.clone();
		this.effectiveSize = effectiveSize.clone();
		this.effectiveOffset = effectiveOffset.clone();
		this.effectiveLocalOffset = effectiveLocalOffset.clone();
		this.numThreads = Runtime.getRuntime().availableProcessors();
		
		long n = blockSize[ 0 ];
		for ( int d = 1; d < numDimensions; ++d )
			n *= blockSize[ d ];

		// split up into many parts for multithreading
		this.portions = FusionHelper.divideIntoPortions( n, Runtime.getRuntime().availableProcessors() * 2 );
		this.taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
	}

	/**
	 * @param source - needs to be extended with an OutOfBounds in case the block extends past the boundaries of the RandomAccessibleInterval
	 * @param block - the Block to copy it to
	 */
	public void copyBlock( final RandomAccessible< FloatType > source, final RandomAccessibleInterval< FloatType > block )
	{
		// set up threads
		final ArrayList< Callable< Boolean > > tasks = new ArrayList< Callable< Boolean > >();

		for ( int i = 0; i < portions.size(); ++i )
		{
			final int threadIdx = i;

			tasks.add( new Callable< Boolean >()
			{
				@SuppressWarnings("unchecked")
				@Override
				public Boolean call() throws Exception
				{
					if ( source.numDimensions() == 3 && ArrayImg.class.isInstance( block ) )
						copy3dArray( threadIdx, numThreads, source, (ArrayImg< FloatType, ?>)block, offset );
					else
					{
						final ImagePortion portion = portions.get( threadIdx );
						copy( portion.getStartPosition(), portion.getLoopSize(), source, block, offset);
					}
					
					return true;
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
			IOFunctions.println( "Failed to copy block: " + e );
			e.printStackTrace();
			return;
		}
	}

	public void pasteBlock( final RandomAccessibleInterval< FloatType > target, final RandomAccessibleInterval< FloatType > block )
	{
		// set up threads
		final ArrayList< Callable< Boolean > > tasks = new ArrayList< Callable< Boolean > >();

		for ( int i = 0; i < portions.size(); ++i )
		{
			final int threadIdx = i;
			
			tasks.add( new Callable< Boolean >()
			{
				@SuppressWarnings("unchecked")
				@Override
				public Boolean call() throws Exception
				{
					if ( target.numDimensions() == 3 && ArrayImg.class.isInstance( target ) && ArrayImg.class.isInstance( block ) )
						paste3d( threadIdx, numThreads, (ArrayImg< FloatType, ?>)target, (ArrayImg< FloatType, ?>)block, effectiveOffset, effectiveSize, effectiveLocalOffset );
					else
					{
						final ImagePortion portion = portions.get( threadIdx );
						paste( portion.getStartPosition(), portion.getLoopSize(), target, block, effectiveOffset, effectiveSize, effectiveLocalOffset );
					}
					
					return true;
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
			IOFunctions.println( "Failed to paste block: " + e );
			e.printStackTrace();
			return;
		}
	}

	private static final void copy( final long start, final long loopSize, final RandomAccessible< FloatType > source, final RandomAccessibleInterval< FloatType > block, final int[] offset )
	{
		final int numDimensions = source.numDimensions();
		final Cursor< FloatType > cursor = Views.iterable( block ).localizingCursor();

		// define where we will query the RandomAccess on the source
		// (we say it is the entire block, although it is just a part of it,
		// but which part depends on the underlying container)
		final long[] min = new long[ numDimensions ];
		final long[] max = new long[ numDimensions ];
		
		for ( int d = 0; d < numDimensions; ++d )
		{
			min[ d ] = offset[ d ];
			max[ d ] = offset[ d ] + block.dimension( d ) - 1;
		}

		final RandomAccess< FloatType > randomAccess = source.randomAccess( new FinalInterval( min, max ) );

		cursor.jumpFwd( start );

		final int[] tmp = new int[ numDimensions ];

		for ( long l = 0; l < loopSize; ++l )
		{
			cursor.fwd();
			cursor.localize( tmp );
			
			for ( int d = 0; d < numDimensions; ++d )
				tmp[ d ] += offset[ d ];
			
			randomAccess.setPosition( tmp );
			cursor.get().set( randomAccess.get() );
		}
	}

	private static final void copy3dArray( final int threadIdx, final int numThreads, final RandomAccessible< FloatType > source, final ArrayImg< FloatType, ? > block, final int[] offset )
	{
		final int w = (int)block.dimension( 0 );
		final int h = (int)block.dimension( 1 );
		final int d = (int)block.dimension( 2 );

		final int offsetX = offset[ 0 ];
		final int offsetY = offset[ 1 ];
		final int offsetZ = offset[ 2 ];
		final float[] blockArray = ((FloatArray)block.update( null ) ).getCurrentStorageArray();

		// define where we will query the RandomAccess on the source
		final FinalInterval interval = new FinalInterval( new long[] { offsetX, offsetY, offsetZ }, new long[] { offsetX + w - 1, offsetY + h - 1, offsetZ + d - 1 } );
		final RandomAccess< FloatType > randomAccess = source.randomAccess( interval );

		final int[] tmp = new int[]{ offsetX, offsetY, 0 };

		for ( int z = threadIdx; z < d; z += numThreads )
		{
			tmp[ 2 ] = z + offsetZ;
			randomAccess.setPosition( tmp );

			int i = z * h * w;

			for ( int y = 0; y < h; ++y )
			{
				randomAccess.setPosition( offsetX, 0 );

				for ( int x = 0; x < w; ++x )
				{
					blockArray[ i++ ] = randomAccess.get().get();
					randomAccess.fwd( 0 );
				}

				randomAccess.move( -w, 0 );
				randomAccess.fwd( 1 );
			}
		}
	}

	private static final void paste( final long start, final long loopSize, final RandomAccessibleInterval< FloatType > target, final RandomAccessibleInterval< FloatType > block, 
			final int[] effectiveOffset, final int[] effectiveSize, final int[] effectiveLocalOffset )
	{
		final int numDimensions = target.numDimensions();
		
		// iterate over effective size
		final LocalizingZeroMinIntervalIterator cursor = new LocalizingZeroMinIntervalIterator( effectiveSize );
		
		// read from block
		final RandomAccess<FloatType> blockRandomAccess  = block.randomAccess();
		
		// write to target		
		final RandomAccess<FloatType> targetRandomAccess  = target.randomAccess();
		
		cursor.jumpFwd( start );
		
		final int[] tmp = new int[ numDimensions ];
		
		for ( long l = 0; l < loopSize; ++l )
		{
			cursor.fwd();
			cursor.localize( tmp );
			
			// move to the relative local offset where the real data starts
			for ( int d = 0; d < numDimensions; ++d )
				tmp[ d ] += effectiveLocalOffset[ d ];
			
			blockRandomAccess.setPosition( tmp );
			
			// move to the right position in the image
			for ( int d = 0; d < numDimensions; ++d )
				tmp[ d ] += effectiveOffset[ d ] - effectiveLocalOffset[ d ];

			targetRandomAccess.setPosition( tmp );

			// write the pixel
			targetRandomAccess.get().set( blockRandomAccess.get() );
		}
	}

	private static final void paste3d( final int threadIdx, final int numThreads, final ArrayImg< FloatType, ? > target, final ArrayImg< FloatType, ? > block, 
			final int[] effectiveOffset, final int[] effectiveSize, final int[] effectiveLocalOffset )
	{
		// min position in the output
		final int minX = effectiveOffset[ 0 ];
		final int minY = effectiveOffset[ 1 ];
		final int minZ = effectiveOffset[ 2 ];

		// max+1 of the output area
		final int maxX = effectiveSize[ 0 ] + minX;
		final int maxY = effectiveSize[ 1 ] + minY;
		final int maxZ = effectiveSize[ 2 ] + minZ;

		// size of the output area
		final int sX = effectiveSize[ 0 ];

		// min position in the output
		final int minXb = effectiveLocalOffset[ 0 ];
		final int minYb = effectiveLocalOffset[ 1 ];
		final int minZb = effectiveLocalOffset[ 2 ];

		// size of the target image
		final int w = (int)target.dimension( 0 );
		final int h = (int)target.dimension( 1 );

		// size of the block image
		final int wb = (int)block.dimension( 0 );
		final int hb = (int)block.dimension( 1 );

		final float[] blockArray = ((FloatArray)block.update( null ) ).getCurrentStorageArray();
		final float[] targetArray = ((FloatArray)target.update( null ) ).getCurrentStorageArray();
				
		for ( int z = minZ + threadIdx; z < maxZ; z += numThreads )
		{
			final int zBlock = z - minZ + minZb;
			
			int iTarget = z * h * w + minY * w + minX;
			int iBlock = zBlock * hb * wb + minYb * wb + minXb;
			
			for ( int y = minY; y < maxY; ++y )
			{			
				for ( int x = minX; x < maxX; ++x )
					targetArray[ iTarget++ ] = blockArray[ iBlock++ ];

				iTarget -= sX;
				iTarget += w;
				
				iBlock -= sX;
				iBlock += wb;
			}
		}
	}
}
