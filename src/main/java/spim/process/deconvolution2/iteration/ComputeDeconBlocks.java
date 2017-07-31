package spim.process.deconvolution2.iteration;

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
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import spim.Threads;
import spim.fiji.ImgLib2Temp.Triple;
import spim.process.cuda.Block;
import spim.process.deconvolution.FirstIteration;
import spim.process.deconvolution2.DeconView;
import spim.process.deconvolution2.DeconViews;
import spim.process.deconvolution2.iteration.ComputeBlockThread.IterationStatistics;
import spim.process.export.DisplayImage;
import spim.process.fusion.FusionTools;
import spim.process.fusion.ImagePortion;

public class ComputeDeconBlocks
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

	public ComputeDeconBlocks(
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
			final AtomicInteger ai = new AtomicInteger();
			final Thread[] threads = new Thread[ computeBlockThreads.size() ];

			final int viewNum = v;
			final int numBlocks = view.getNumBlocks();

			final IterationStatistics[] stats = new IterationStatistics[ numBlocks ];

			for ( int t = 0; t < computeBlockThreads.size(); ++t )
			{
				final int threadId = t;

				threads[ threadId ] = new Thread( new Runnable()
				{
					public void run()
					{
						// one ComputeBlockThread creates a temporary image for I/O, valid throughout the whole cycle
						final ComputeBlockThread blockThread = computeBlockThreads.get( threadId );
						final Img< FloatType > blockPsiImg = blockThread.getBlockFactory().create( Util.int2long( blockThread.getBlockSize() ), new FloatType() );

						int blockId;

						while ( ( blockId = ai.getAndIncrement() ) < numBlocks )
						{
							final Block blockStruct = view.getBlocks()[ blockId ];
							System.out.println( " block " + blockId + ", " + Util.printInterval( blockStruct ) );

							long time = System.currentTimeMillis();
							blockStruct.copyBlock( Views.extendMirrorSingle( psi ), blockPsiImg );
							System.out.println( " block " + blockId + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): copy " + (System.currentTimeMillis() - time) );

							time = System.currentTimeMillis();
							stats[ blockId ] = blockThread.runIteration(
									view,
									blockStruct,
									blockPsiImg,
									Views.zeroMin( Views.interval( Views.extendZero( view.getImage() ), blockStruct ) ),//imgBlock,
									Views.zeroMin( Views.interval( Views.extendZero( view.getWeight() ), blockStruct ) ),//weightBlock,
									max[ viewNum ],
									view.getPSF().getKernel1(),
									view.getPSF().getKernel2() );
							System.out.println( " block " + blockId + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): compute " + (System.currentTimeMillis() - time) );

							time = System.currentTimeMillis();
							blockStruct.pasteBlock( psi, blockPsiImg );
							System.out.println( " block " + blockId + ", thread (" + (threadId+1) + "/" + threads.length + "), (CPU): paste " + (System.currentTimeMillis() - time) );
						}

						// accumulate the results from the individual threads
						IterationStatistics is = new IterationStatistics();

						for ( int i = 0; i < stats.length; ++i )
						{
							is.sumChange += stats[ i ].sumChange;
							is.maxChange = Math.max( is.maxChange, stats[ i ].maxChange );
						}

						IOFunctions.println( "iteration: " + it + ", view: " + view + " --- sum change: " + is.sumChange + " --- max change per pixel: " + is.maxChange );
					}
				});
			}
	
			for ( int ithread = 0; ithread < threads.length; ++ithread )
				threads[ ithread ].start();
	
			try
			{
				for ( int ithread = 0; ithread < threads.length; ++ithread )
					threads[ ithread ].join();
			}
			catch ( InterruptedException ie )
			{
				throw new RuntimeException(ie);
			}

			++v;
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
