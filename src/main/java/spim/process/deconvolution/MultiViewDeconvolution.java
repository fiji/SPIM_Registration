package spim.process.deconvolution;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import spim.Threads;
import spim.fiji.ImgLib2Temp.Triple;
import spim.process.cuda.Block;
import spim.process.deconvolution.iteration.ComputeBlockThread;
import spim.process.deconvolution.iteration.ComputeBlockThreadFactory;
import spim.process.deconvolution.iteration.ComputeBlockThread.IterationStatistics;
import spim.process.deconvolution.util.FirstIteration;
import spim.process.export.DisplayImage;
import spim.process.fusion.FusionTools;
import spim.process.fusion.ImagePortion;

public class MultiViewDeconvolution
{
	final public static float outsideValueImg = 0f; // the value the input image has if there is no data at this pixel
	final public static float minValueImg = 1f; // mininal value for the input image (as it is not normalized)
	final public static float minValue = 0.0001f; // minimal value for the deconvolved image

	public static boolean debug = true;
	public static int debugInterval = 1;

	// current iteration
	int it = 0;

	// the multi-view deconvolved image
	final Img< FloatType > psi;

	// the input data
	final DeconViews views;

	// the thread that will compute the iteration for each block independently
	final ComputeBlockThreadFactory computeBlockFactory;

	// the actual block compute threads
	final ArrayList< ComputeBlockThread > computeBlockThreads;

	// max intensities for each contributing view, ordered as in views
	final float[] max;

	// for debug
	ImageStack stack;
	CompositeImage ci;

	public MultiViewDeconvolution(
			final DeconViews views,
			final int numIterations,
			final ComputeBlockThreadFactory computeBlockFactory,
			final ImgFactory< FloatType > psiFactory )
	{
		this.computeBlockFactory = computeBlockFactory;
		this.views = views;
		this.max = new float[ views.getViews().size() ];

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Deconvolved image factory: " + psiFactory.getClass().getSimpleName() );

		this.psi = psiFactory.create( views.getPSIDimensions(), new FloatType() );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up " + computeBlockFactory.numParallelBlocks() + " Block Thread(s), using '" + computeBlockFactory.getClass().getSimpleName() + "'" );

		this.computeBlockThreads = new ArrayList<>();

		for ( int i = 0; i < computeBlockFactory.numParallelBlocks(); ++i )
			computeBlockThreads.add( computeBlockFactory.create( i ) );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusing image for first iteration" );

		double avg = fuseFirstIteration( psi, views.getViews(), views.getExecutorService(), max );

		double avgMax = 0;
		for ( int i = 0; i < max.length; ++i )
		{
			avgMax += max[ i ];
			IOFunctions.println( "Max intensity in overlapping area of view " + i + ": " + max[ i ] );
		}
		avgMax /= (double)max.length;

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Average intensity in overlapping area: " + avg );

		if ( Double.isNaN( avg ) )
		{
			avg = 1.0;
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): ERROR! Computing average FAILED, is NaN, setting it to: " + avg );
		}

		// run the deconvolution
		while ( it < numIterations )
		{
			// show the fused image first
			if ( debug && ( it-1 ) % debugInterval == 0 )
			{
				// if it is slices, wrap & copy otherwise virtual & copy - never use the actual image
				// as it is being updated in the process
				final ImagePlus tmp = DisplayImage.getImagePlusInstance( psi, true, "Psi", 0, avgMax ).duplicate();

				if ( this.stack == null )
				{
					this.stack = tmp.getImageStack();
					for ( int i = 0; i < this.psi.dimension( 2 ); ++i )
						this.stack.setSliceLabel( "Iteration 1", i + 1 );

					tmp.setTitle( "debug view" );
					this.ci = new CompositeImage( tmp, CompositeImage.COMPOSITE );
					this.ci.setDimensions( 1, (int)this.psi.dimension( 2 ), 1 );
					this.ci.setDisplayMode( IJ.GRAYSCALE );
					this.ci.show();
				}
				else if ( stack.getSize() == this.psi.dimension( 2 ) )
				{
					final ImageStack t = tmp.getImageStack();
					for ( int i = 0; i < this.psi.dimension( 2 ); ++i )
						this.stack.addSlice( "Iteration 2", t.getProcessor( i + 1 ) );
					this.ci.hide();

					this.ci = new CompositeImage( new ImagePlus( "debug view", this.stack ), CompositeImage.COMPOSITE );
					this.ci.setDimensions( 1, (int)this.psi.dimension( 2 ), 2 );
					this.ci.setDisplayMode( IJ.GRAYSCALE );
					this.ci.show();
				}
				else
				{
					final ImageStack t = tmp.getImageStack();
					for ( int i = 0; i < this.psi.dimension( 2 ); ++i )
						this.stack.addSlice( "Iteration " + i, t.getProcessor( i + 1 ) );

					this.ci.setStack( this.stack, 1, (int)this.psi.dimension( 2 ), stack.getSize() / (int)this.psi.dimension( 2 ) );
				}
			}

			if ( it == 0 )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting image to average intensity: " + avg );

				for ( final FloatType t : psi )
					t.set( (float)avg );
			}

			runNextIteration();
		}

		// TODO: IOFunctions.println( "Masking never updated pixels." );
		// maskNeverUpdatedPixels( tmp1, views.getViews() );

		IOFunctions.println( "DONE (" + new Date(System.currentTimeMillis()) + ")." );
	}

	public Img< FloatType > getPSI() { return psi; }

	public void runNextIteration()
	{
		++it;

		IOFunctions.println( "iteration: " + it + " (" + new Date(System.currentTimeMillis()) + ")" );

		int v = 0;

		for ( final DeconView view : views.getViews() )
		{
			final int viewNum = v;

			final int totalNumBlocks = view.getNumBlocks();
			final Vector< IterationStatistics > stats = new Vector<>();

			int currentTotalBlock = 0;

			// keep thelast blocks to be written back to the global psi image once it is not overlapping anymore
			final Vector< Pair< Pair< Integer, Block >, Img< FloatType > > > previousBlockWritebackQueue = new Vector<>();
			final Vector< Pair< Pair< Integer, Block >, Img< FloatType > > > currentBlockWritebackQueue = new Vector<>();

			int batch = 0;
			for ( final List< Block > blocksBatch : view.getNonInterferingBlocks() )
			{
				final int numBlocksBefore = currentTotalBlock;
				final int numBlocksBatch = blocksBatch.size();
				currentTotalBlock += numBlocksBatch;

				System.out.println( "Processing " + numBlocksBatch + " blocks from batch " + (++batch) + "/" + view.getNonInterferingBlocks().size() );

				final AtomicInteger ai = new AtomicInteger();
				final Thread[] threads = new Thread[ computeBlockThreads.size() ];

				for ( int t = 0; t < computeBlockThreads.size(); ++t )
				{
					final int threadId = t;
	
					threads[ threadId ] = new Thread( new Runnable()
					{
						public void run()
						{
							// one ComputeBlockThread creates a temporary image for I/O, valid throughout the whole cycle
							final ComputeBlockThread blockThread = computeBlockThreads.get( threadId );
	
							int blockId;

							while ( ( blockId = ai.getAndIncrement() ) < numBlocksBatch )
							{
								final int blockIdOut = blockId + numBlocksBefore;

								final Block blockStruct = blocksBatch.get( blockId );
								System.out.println( " block " + blockIdOut + ", " + Util.printInterval( blockStruct ) );

								long time = System.currentTimeMillis();
								blockStruct.copyBlock( Views.extendMirrorSingle( psi ), blockThread.getPsiBlockTmp() );
								System.out.println( " block " + blockIdOut + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): copy " + (System.currentTimeMillis() - time) );

								time = System.currentTimeMillis();
								stats.add( blockThread.runIteration(
										view,
										blockStruct,
										Views.zeroMin( Views.interval( Views.extendZero( view.getImage() ), blockStruct ) ),//imgBlock,
										Views.zeroMin( Views.interval( Views.extendZero( view.getWeight() ), blockStruct ) ),//weightBlock,
										max[ viewNum ],
										view.getPSF().getKernel1(),
										view.getPSF().getKernel2() ) );
								System.out.println( " block " + blockIdOut + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): compute " + (System.currentTimeMillis() - time) );
	
								time = System.currentTimeMillis();
								if ( totalNumBlocks == 1 )
								{
									blockStruct.pasteBlock( psi, blockThread.getPsiBlockTmp() );
									System.out.println( " block " + blockIdOut + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): paste " + (System.currentTimeMillis() - time) );
								}
								else
								{
									// copy to the writequeue
									final Img< FloatType > tmp = blockThread.getPsiBlockTmp().factory().create( blockThread.getPsiBlockTmp(), new FloatType() );
									FusionTools.copyImg( blockThread.getPsiBlockTmp(), tmp, false, views.getExecutorService() );
									currentBlockWritebackQueue.add( new ValuePair<>( new ValuePair<>( blockIdOut, blockStruct ), tmp ) );

									System.out.println( " block " + blockIdOut + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): saving for later pasting " + (System.currentTimeMillis() - time) );
								}
							}
						}
					});
				}

				// run the threads that process all blocks of this batch in parallel (often, this will be just one thread)
				runBlockThreads( threads );

				// write back previous list of blocks
				writeBack( psi, previousBlockWritebackQueue );

				previousBlockWritebackQueue.clear();
				previousBlockWritebackQueue.addAll( currentBlockWritebackQueue );
				currentBlockWritebackQueue.clear();

				//DisplayImage.getImagePlusInstance( psi, false, it + ", view=" + viewNum + ", batch=" + batch, Double.NaN, Double.NaN ).show();;
			} // finish one block batch

			// write back last list of blocks
			writeBack( psi, previousBlockWritebackQueue );

			// accumulate the results from the individual blocks
			final IterationStatistics is = new IterationStatistics();

			for ( int i = 0; i < stats.size(); ++i )
			{
				is.sumChange += stats.get( i ).sumChange;
				is.maxChange = Math.max( is.maxChange, stats.get( i ).maxChange );
			}

			IOFunctions.println( "iteration: " + it + ", view: " + viewNum + " --- sum change: " + is.sumChange + " --- max change per pixel: " + is.maxChange );
			++v;
		}// finish view
	}

	protected static void runBlockThreads( final Thread[] threads )
	{
		for ( int ithread = 0; ithread < threads.length; ++ithread )
			threads[ ithread ].start();

		try
		{
			for ( int ithread = 0; ithread < threads.length; ++ithread )
				threads[ ithread ].join();
		}
		catch ( InterruptedException ie ) { throw new RuntimeException(ie); }
	}

	protected static final void writeBack( final Img< FloatType > psi, final Vector< Pair< Pair< Integer, Block >, Img< FloatType > > > blockWritebackQueue )
	{
		for ( final Pair< Pair< Integer, Block >, Img< FloatType > > writeBackBlock : blockWritebackQueue )
		{
			long time = System.currentTimeMillis();
			writeBackBlock.getA().getB().pasteBlock( psi, writeBackBlock.getB() );
			System.out.println( " block " + writeBackBlock.getA().getA() + ", (CPU): paste " + (System.currentTimeMillis() - time) );
		}
	}

	protected static final double fuseFirstIteration( final Img< FloatType > psi, final List< DeconView > views, final ExecutorService service, final float[] max )
	{
		final int nThreads = Threads.numThreads();
		final int nPortions = nThreads * 20;

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionTools.divideIntoPortions( psi.size(), nPortions );
		final ArrayList< Callable< Triple< RealSum, Long, float[] > > > tasks = new ArrayList< Callable< Triple< RealSum, Long, float[] > > >();

		final ArrayList< RandomAccessibleInterval< FloatType > > imgs = new ArrayList< RandomAccessibleInterval< FloatType > >();

		for ( final DeconView mvdecon : views )
			imgs.add( mvdecon.getImage() );

		for ( final ImagePortion portion : portions )
			tasks.add( new FirstIteration( portion, psi, imgs ) );

		final RealSum s = new RealSum();
		long count = 0;

		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< Triple< RealSum, Long, float[] > > > imgIntensities = service.invokeAll( tasks );

			for ( final Future< Triple< RealSum, Long, float[] >  > future : imgIntensities )
			{
				s.add( future.get().getA().getSum() );
				count += future.get().getB().longValue();

				if ( max != null )
					for ( int i = 0; i < max.length; ++i )
						max[ i ] = Math.max( max[ i ], future.get().getC()[ i ] );
			}
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to fuse initial iteration: " + e );
			e.printStackTrace();
			return -1;
		}

		return s.getSum() / (double)count;
	}

}
