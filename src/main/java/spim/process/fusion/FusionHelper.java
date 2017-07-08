package spim.process.fusion;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.Threads;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.process.fusion.weightedavg.ProcessFusion;

public class FusionHelper
{
	public static enum ImgDataType { VIRTUAL, CACHED, PRECOMPUTED };
	public static String[] imgDataTypeChoice = new String[]{ "Virtual", "Cached", "Precompute Image" };

	/**
	 * Do not instantiate
	 */
	private FusionHelper() {}

	public static void adjustBlending( final BasicViewDescription< ? extends BasicViewSetup > vd, final float[] blending, final float[] border )
	{
		final float minRes = (float)getMinRes( vd );
		VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSize( vd.getViewSetup() );
		if ( voxelSize == null )
			voxelSize = new FinalVoxelDimensions( "px", new double[]{ 1, 1, 1 } );

		if ( ProcessFusion.defaultAdjustBlendingForAnisotropy )
		{
			for ( int d = 0; d < blending.length; ++d )
			{
				blending[ d ] /= ( float ) voxelSize.dimension( d ) / minRes;
				border[ d ] /= ( float ) voxelSize.dimension( d ) / minRes;
			}
		}
	}

	public static double getMinRes( final BasicViewDescription< ? extends BasicViewSetup > desc )
	{
		final VoxelDimensions size = ViewSetupUtils.getVoxelSize( desc.getViewSetup() );

		if ( size == null )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): WARNINIG, could not load voxel size!! Assuming 1,1,1"  );
			return 1;
		}

		return Math.min( size.dimension( 0 ), Math.min( size.dimension( 1 ), size.dimension( 2 ) ) );
	}

	public static String getIllumName( final List< Illumination > illumsToProcess )
	{
		String illumName = "_Ill" + illumsToProcess.get( 0 ).getName();

		for ( int i = 1; i < illumsToProcess.size(); ++i )
			illumName += "," + illumsToProcess.get( i ).getName();

		return illumName;
	}

	public static String getAngleName( final List< Angle > anglesToProcess )
	{
		String angleName = "_Ang" + anglesToProcess.get( 0 ).getName();

		for ( int i = 1; i < anglesToProcess.size(); ++i )
			angleName += "," + anglesToProcess.get( i ).getName();

		return angleName;
	}

	public static final ArrayList< ViewDescription > assembleInputData(
			final SpimData2 spimData,
			final TimePoint timepoint,
			final Channel channel,
			final List< ViewId > viewIdsToProcess )
	{
		final ArrayList< ViewDescription > inputData = new ArrayList< ViewDescription >();

		for ( final ViewId viewId : viewIdsToProcess )
		{
			final ViewDescription vd = spimData.getSequenceDescription().getViewDescription(
					viewId.getTimePointId(), viewId.getViewSetupId() );

			if ( !vd.isPresent() || vd.getTimePointId() != timepoint.getId() || vd.getViewSetup().getChannel().getId() != channel.getId() )
				continue;

			// get the most recent model
			spimData.getViewRegistrations().getViewRegistration( viewId ).updateModel();

			inputData.add( vd );
		}

		return inputData;
	}

	public static < T extends NativeType< T > > RandomAccessibleInterval< T > cacheRandomAccessibleInterval(
			final RandomAccessibleInterval< T > input,
			final long maxCacheSize,
			final T type,
			final int... cellDim )
	{
		final RandomAccessibleInterval< T > in;

		if ( Views.isZeroMin( input ) )
			in = input;
		else
			in = Views.zeroMin( input );
		
		final ReadOnlyCachedCellImgOptions options = new ReadOnlyCachedCellImgOptions().cellDimensions( cellDim ).maxCacheSize( maxCacheSize );
		final ReadOnlyCachedCellImgFactory factory = new ReadOnlyCachedCellImgFactory( options );

		final CellLoader< T > loader = new CellLoader< T >()
		{
			@Override
			public void load( final SingleCellArrayImg< T, ? > cell ) throws Exception
			{
				final Cursor< T > cursor = cell.localizingCursor();
				final RandomAccess< T > ra = in.randomAccess();
				
				while( cursor.hasNext() )
				{
					cursor.fwd();
					ra.setPosition( cursor );
					cursor.get().set( ra.get() );
				}
			}
		};

		final long[] dim = new long[ in.numDimensions() ];
		in.dimensions( dim );

		return translateIfNecessary( input, factory.create( dim, type, loader ) );
	}

	public static RandomAccessibleInterval< FloatType > copyImg( final RandomAccessibleInterval< FloatType > input, final ImgFactory< FloatType > factory )
	{
		return translateIfNecessary( input, copyImgNoTranslation( input, factory ) );
	}

	public static Img< FloatType > copyImgNoTranslation( final RandomAccessibleInterval< FloatType > input, final ImgFactory< FloatType > factory )
	{
		final RandomAccessibleInterval< FloatType > in;

		if ( Views.isZeroMin( input ) )
			in = input;
		else
			in = Views.zeroMin( input );

		final long[] dim = new long[ in.numDimensions() ];
		in.dimensions( dim );

		final Img< FloatType > tImg = factory.create( dim, new FloatType() );

		// copy the virtual construct into an actual image
		FusionHelper.copyImg( in, tImg );

		return tImg;
	}

	public static < T > RandomAccessibleInterval< T > translateIfNecessary( final Interval original, final RandomAccessibleInterval< T > copy )
	{
		if ( Views.isZeroMin( original ) )
		{
			return copy;
		}
		else
		{
			final long[] min = new long[ original.numDimensions() ];
			original.min( min );

			return Views.translate( copy, min );
		}
	}

	public static void copyImg( final RandomAccessibleInterval< FloatType > input, final RandomAccessibleInterval< FloatType > output )
	{
		final int nThreads = Threads.numThreads();
		final int nPortions = nThreads * 2;
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( Views.iterable( input ).size(), nPortions );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					copyImg( portion.getStartPosition(), portion.getLoopSize(), input, output );
					return null;
				}
			});
		}

		execTasks( tasks, nThreads, "copy image" );
	}

	public static final void execTasks( final ArrayList< Callable< Void > > tasks, final int nThreads, final String jobDescription )
	{
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( nThreads );

		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			IOFunctions.println( "Failed to " + jobDescription + ": " + e );
			e.printStackTrace();
			return;
		}

		taskExecutor.shutdown();
	}


	/*
	 * One thread of a method to compute the quotient between two images of the multiview deconvolution
	 * 
	 * @param start
	 * @param loopSize
	 * @param source
	 * @param target
	 */
	public static final void copyImg(
			final long start,
			final long loopSize,
			final RandomAccessibleInterval< FloatType > source,
			final RandomAccessibleInterval< FloatType > target )
	{
		final IterableInterval< FloatType > sourceIterable = Views.iterable( source );
		final IterableInterval< FloatType > targetIterable = Views.iterable( target );

		if ( sourceIterable.iterationOrder().equals( targetIterable.iterationOrder() ) )
		{
			final Cursor< FloatType > cursorSource = sourceIterable.cursor();
			final Cursor< FloatType > cursorTarget = targetIterable.cursor();
	
			cursorSource.jumpFwd( start );
			cursorTarget.jumpFwd( start );
	
			for ( long l = 0; l < loopSize; ++l )
				cursorTarget.next().set( cursorSource.next() );
		}
		else
		{
			final RandomAccess< FloatType > raSource = source.randomAccess();
			final Cursor< FloatType > cursorTarget = targetIterable.localizingCursor();

			cursorTarget.jumpFwd( start );

			for ( long l = 0; l < loopSize; ++l )
			{
				cursorTarget.fwd();
				raSource.setPosition( cursorTarget );

				cursorTarget.get().set( raSource.get() );
			}
		}
	}

	public static < T extends RealType< T > > float[] minMax( final RandomAccessibleInterval< T > img )
	{
		final IterableInterval< T > iterable = Views.iterable( img );
		
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( iterable.size(), Threads.numThreads() * 2 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< float[] > > tasks = new ArrayList< Callable< float[] > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< float[] >() 
					{
						@Override
						public float[] call() throws Exception
						{
							float min = Float.MAX_VALUE;
							float max = -Float.MAX_VALUE;
							
							final Cursor< T > c = iterable.cursor();
							c.jumpFwd( portion.getStartPosition() );
							
							for ( long j = 0; j < portion.getLoopSize(); ++j )
							{
								final float v = c.next().getRealFloat();
								
								min = Math.min( min, v );
								max = Math.max( max, v );
							}
							
							// min & max of this portion
							return new float[]{ min, max };
						}
					});
		}
		
		// run threads and combine results
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;
		
		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< float[] > > futures = taskExecutor.invokeAll( tasks );
			
			for ( final Future< float[] > future : futures )
			{
				final float[] minmax = future.get();
				min = Math.min( min, minmax[ 0 ] );
				max = Math.max( max, minmax[ 1 ] );
			}
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to compute min/max: " + e );
			e.printStackTrace();
			return null;
		}

		taskExecutor.shutdown();
		
		return new float[]{ min, max };
	}

	/**
	 * Normalizes the image to the range [0...1]
	 * 
	 * @param img - the image to normalize
	 * @return - normalized array
	 */
	public static boolean normalizeImage( final RandomAccessibleInterval< FloatType > img )
	{
		final float minmax[] = minMax( img );
		final float min = minmax[ 0 ];
		final float max = minmax[ 1 ];
		
		return normalizeImage( img, min, max );
	}

	/**
	 * Normalizes the image to the range [0...1]
	 * 
	 * @param img - the image to normalize
	 * @param min - min value
	 * @param max - max value
	 * @return - normalized array
	 */
	public static boolean normalizeImage( final RandomAccessibleInterval< FloatType > img, final float min, final float max )
	{
		final float diff = max - min;

		if ( Float.isNaN( diff ) || Float.isInfinite(diff) || diff == 0 )
		{
			IOFunctions.println( "Cannot normalize image, min=" + min + "  + max=" + max );
			return false;
		}

		final IterableInterval< FloatType > iterable = Views.iterable( img );
		
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( iterable.size(), Threads.numThreads() * 2 );

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
							final Cursor< FloatType > c = iterable.cursor();
							c.jumpFwd( portion.getStartPosition() );
							
							for ( long j = 0; j < portion.getLoopSize(); ++j )
							{
								final FloatType t = c.next();
								
								final float norm = ( t.get() - min ) / diff;
								t.set( norm );
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
			IOFunctions.println( "Failed to compute min/max: " + e );
			e.printStackTrace();
			return false;
		}

		taskExecutor.shutdown();
		
		return true;
	}


	public static final Vector<ImagePortion> divideIntoPortions( final long imageSize, final int numPortions )
	{
		final long threadChunkSize = imageSize / numPortions;
		final long threadChunkMod = imageSize % numPortions;
		
		final Vector<ImagePortion> portions = new Vector<ImagePortion>();
		
		for ( int portionID = 0; portionID < numPortions; ++portionID )
		{
			// move to the starting position of the current thread
			final long startPosition = portionID * threadChunkSize;

			// the last thread may has to run longer if the number of pixels cannot be divided by the number of threads
			final long loopSize;
			if ( portionID == numPortions - 1 )
				loopSize = threadChunkSize + threadChunkMod;
			else
				loopSize = threadChunkSize;
			
			portions.add( new ImagePortion( startPosition, loopSize ) );
		}
		
		return portions;
	}

}
