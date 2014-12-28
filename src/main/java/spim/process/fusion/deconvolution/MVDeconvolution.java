package spim.process.fusion.deconvolution;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import spim.Threads;
import spim.fiji.spimdata.imgloaders.StackImgLoaderIJ;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.deconvolution.MVDeconFFT.PSFTYPE;
import spim.process.fusion.export.DisplayImage;

public class MVDeconvolution
{
	// if you want to start from a certain iteration
	public static String initialImage = null;
	
	// check in advance if values are reasonable
	public static boolean checkNumbers = true;
	
	public static boolean debug = true;
	public static int debugInterval = 1;
	final static float minValue = 0.0001f;

	final int numViews, numDimensions;
	final float avg;
	final double lambda;

	ImageStack stack;
	CompositeImage ci;

	boolean collectStatistics = true;

	// current iteration
	int i = 0;

	// the multi-view deconvolved image
	Img< FloatType > psi;

	// the input data
	final MVDeconInput views;
	ArrayList< MVDeconFFT > data;
	String name;
	
	public MVDeconvolution(
			final MVDeconInput views,
			final PSFTYPE iterationType,
			final int numIterations,
			final double lambda,
			double osemspeedup,
			final int osemspeedupindex,
			final String name )
	{
		this.name = name;
		this.data = views.getViews();
		this.views = views;
		this.numViews = data.size();
		this.numDimensions = data.get( 0 ).getImage().numDimensions();
		this.lambda = lambda;

		if ( initialImage != null )
			this.psi = loadInitialImage(
					initialImage,
					checkNumbers,
					minValue,
					data.get( 0 ).getImage(),
					data.get( 0 ).getImage().factory() );

		final double[] result = AdjustInput.normAllImages( data );
		this.avg = (float)result[ 0 ];

		if ( osemspeedupindex == 1 )//min
			osemspeedup = Math.max( 1, result[ 1 ] );//but not smaller than 1
		else if ( osemspeedupindex == 2 )//avg
			osemspeedup = Math.max( 1, result[ 2 ] );//but not smaller than 1

		adjustOSEMspeedup( views, osemspeedup );

		IJ.log( "Average intensity in overlapping area: " + avg );
		IJ.log( "OSEM acceleration: " + osemspeedup );

		// init all views
		views.init( iterationType );

		//
		// the real data image psi is initialized with the average 
		// if there was no initial guess loaded
		//
		if ( this.psi == null )
		{
			this.psi = data.get( 0 ).getImage().factory().create( data.get( 0 ).getImage(), data.get( 0 ).getImage().firstElement() );

			for ( final FloatType f : psi )
				f.set( avg );
		}

		IOFunctions.println( "Deconvolved image container: " + psi.getClass().getSimpleName() );

		//this.stack = new ImageStack( this.psi.getDimension( 0 ), this.psi.getDimension( 1 ) );

		// run the deconvolution
		while ( i < numIterations )
		{
			runIteration();

			if ( debug && (i-1) % debugInterval == 0 )
			{
				// if it is slices, wrap & copy otherwise virtual & copy - never use the actual image
				// as it is being updated in the process
				final ImagePlus tmp = DisplayImage.getImagePlusInstance( psi, true, "Psi", 0, 1 ).duplicate();

				if ( this.stack == null )
				{
					this.stack = tmp.getImageStack();
					for ( int i = 0; i < this.psi.dimension( 2 ); ++i )
						this.stack.setSliceLabel( "Iteration 1", i + 1 );

					tmp.setTitle( "debug view" );
					this.ci = new CompositeImage( tmp, CompositeImage.COMPOSITE );
					this.ci.setDimensions( 1, (int)this.psi.dimension( 2 ), 1 );
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
		}

		IOFunctions.println( "DONE (" + new Date(System.currentTimeMillis()) + ")." );
	}
	
	
	private void adjustOSEMspeedup( final MVDeconInput views, final double osemspeedup )
	{
		if ( osemspeedup == 1.0 )
			return;

		for ( final MVDeconFFT view : views.getViews() )
		{
			for ( final FloatType f : view.getWeight() )
				f.set( Math.min( 1, f.get() * (float)osemspeedup ) ); // individual contribution never higher than 1
		}
	}


	protected static Img< FloatType > loadInitialImage(
			final String fileName,
			final boolean checkNumbers,
			final float minValue,
			final Dimensions dimensions,
			final ImgFactory< FloatType > imageFactory )
	{
		IOFunctions.println( "Loading image '" + fileName + "' as start for iteration." );

		final ImagePlus impPSI = StackImgLoaderIJ.open( new File( fileName ) );

		if ( impPSI == null )
		{
			IOFunctions.println( "Could not load image '" + fileName + "'." );
			return null;
		}

		final long[] dimPsi = impPSI.getStack().getSize() == 1 ? 
				new long[]{ impPSI.getWidth(), impPSI.getHeight() } : new long[]{ impPSI.getWidth(), impPSI.getHeight(), impPSI.getStack().getSize() };
		final Img< FloatType > psi = imageFactory.create( dimPsi, new FloatType() );
		StackImgLoaderIJ.imagePlus2ImgLib2Img( impPSI, psi, false );

		if ( psi == null )
		{
			IOFunctions.println( "Could not load image '" + fileName + "'." );
			return null;
		}
		else
		{
			boolean dimensionsMatch = true;

			final long dim[] = new long[ dimensions.numDimensions() ];

			for ( int d = 0; d < psi.numDimensions(); ++d )
			{
				if ( psi.dimension( d ) != dimensions.dimension( d ) )
					dimensionsMatch = false;

				dim[ d ] = dimensions.dimension( d );
			}

			if ( !dimensionsMatch )
			{
				IOFunctions.println(
						"Dimensions of '" + fileName + "' do not match: " +
						Util.printCoordinates( dimPsi ) + " != " + Util.printCoordinates( dim ) );
				return null;
			}

			if ( checkNumbers )
			{
				IOFunctions.println(
						"Checking values of '" + fileName + "' you can disable this check by setting " +
						"spim.process.fusion.deconvolution.MVDeconvolution.checkNumbers = false;" );

				boolean smaller = false;
				boolean hasZerosOrNeg = false;
				
				for ( final FloatType v : psi )
				{
					if ( v.get() < minValue )
						smaller = true;
	
					if ( v.get() <= 0 )
					{
						hasZerosOrNeg = true;
						v.set( minValue );
					}
				}
	
				if ( smaller )
					IOFunctions.println(
							"Some values '" + fileName + "' are smaller than the minimal value of " +
							minValue + ", this can lead to instabilities." );
	
				if ( hasZerosOrNeg )
					IOFunctions.println(
							"Some values '" + fileName + "' were smaller or equal to zero," +
							"they have been replaced with the min value of " + minValue );
			}
		}

		return psi;
	}

	public MVDeconInput getData() { return views; }
	public String getName() { return name; }
	public double getAvg() { return avg; }

	public Img< FloatType > getPsi() { return psi; }	
	public int getCurrentIteration() { return i; }

	public void runIteration() 
	{
		runIteration( psi, data, lambda, minValue, collectStatistics, i++ );
	}

	final private static void runIteration(
			final Img< FloatType> psi,
			final ArrayList< MVDeconFFT > data,
			final double lambda,
			final float minValue,
			final boolean collectStatistic,
			final int iteration )
	{
		IOFunctions.println( "iteration: " + iteration + " (" + new Date(System.currentTimeMillis()) + ")" );

		final int numViews = data.size();
		final int nThreads = Threads.numThreads();
		final int nPortions = nThreads * 2;

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( psi.size(), nPortions );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		final Img< FloatType > lastIteration;
		
		if ( collectStatistic )
			lastIteration = psi.copy();
		else
			lastIteration = null;

		for ( int view = 0; view < numViews; ++view )
		{
			final MVDeconFFT processingData = data.get( view );

			//
			// convolve psi (current guess of the image) with the PSF of the current view
			//
			final Img< FloatType > psiBlurred = processingData.convolve1( psi );

			//
			// compute quotient img/psiBlurred
			//
			for ( final ImagePortion portion : portions )
			{
				tasks.add( new Callable< Void >()
				{
					@Override
					public Void call() throws Exception
					{
						computeQuotient( portion.getStartPosition(), portion.getLoopSize(), psiBlurred, processingData );
						return null;
					}
				});
			}

			execTasks( tasks, nThreads, "compute quotient" );

			//
			// blur the residuals image with the kernel
			//
			final Img< FloatType > integral = processingData.convolve2( psiBlurred );

			//
			// compute final values
			//
			tasks.clear();

			for ( final ImagePortion portion : portions )
			{
				tasks.add( new Callable< Void >()
				{
					@Override
					public Void call() throws Exception
					{
						computeFinalValues( portion.getStartPosition(), portion.getLoopSize(), psi, integral, processingData.getWeight(), lambda );
						return null;
					}
				});
			}

			execTasks( tasks, nThreads, "compute final values" );
		}

		if ( collectStatistic )
		{
			final double[][] sumMax = new double[ nPortions ][ 2 ];
			tasks.clear();

			for ( int i = 0; i < portions.size(); ++i )
			{
				final ImagePortion portion = portions.get( i );
				final int portionId = i;

				tasks.add( new Callable< Void >()
				{
					@Override
					public Void call() throws Exception
					{
						collectStatistics( portion.getStartPosition(), portion.getLoopSize(), psi, lastIteration, sumMax[ portionId ] );
						return null;
					}
				});
			}

			execTasks( tasks, nThreads, "collect statistics" );

			// accumulate the results from the individual threads
			double sumChange = 0;
			double maxChange = -1;

			for ( int i = 0; i < nPortions; ++i )
			{
				sumChange += sumMax[ i ][ 0 ];
				maxChange = Math.max( maxChange, sumMax[ i ][ 1 ] );
			}

			IOFunctions.println( "iteration: " + iteration + " --- sum change: " + sumChange + " --- max change per pixel: " + maxChange );
		}
	}

	private static final void execTasks( final ArrayList< Callable< Void > > tasks, final int nThreads, final String jobDescription )
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

	/**
	 * One thread of a method to collect statistics for each iteration of the multiview deconvolution
	 * 
	 * @param start
	 * @param loopSize
	 * @param psi
	 * @param lastIteration
	 * @param sumMax
	 */
	private static final void collectStatistics(
			final long start,
			final long loopSize,
			final Img< FloatType > psi,
			final Img< FloatType > lastIteration,
			final double[] sumMax )
	{
		double sumChange = 0;
		double maxChange = -1;
		
		final Cursor< FloatType > cursorPsi = psi.cursor();
		final Cursor< FloatType > cursorLast = lastIteration.cursor();
		
		cursorPsi.jumpFwd( start );
		cursorLast.jumpFwd( start );
		
		for ( long l = 0; l < loopSize; ++l )
		{
			final float last = cursorLast.next().get();
			final float next = cursorPsi.next().get();

			final float change = Math.abs( next - last );

			sumChange += change;
			maxChange = Math.max( maxChange, change );
		}

		sumMax[ 0 ] = sumChange;
		sumMax[ 1 ] = maxChange;
	}

	/**
	 * One thread of a method to compute the quotient between two images of the multiview deconvolution
	 * 
	 * @param start
	 * @param loopSize
	 * @param psiBlurred
	 * @param processingData
	 */
	private static final void computeQuotient(
			final long start,
			final long loopSize,
			final Img< FloatType > psiBlurred,
			final MVDeconFFT processingData )
	{
		final Cursor< FloatType > cursorImg = processingData.getImage().cursor();
		final Cursor< FloatType > cursorPsiBlurred = psiBlurred.cursor();

		cursorImg.jumpFwd( start );
		cursorPsiBlurred.jumpFwd( start );

		for ( long l = 0; l < loopSize; ++l )
		{
			cursorImg.fwd();
			cursorPsiBlurred.fwd();

			final float imgValue = cursorImg.get().get();
			final float psiBlurredValue = cursorPsiBlurred.get().get();

			cursorPsiBlurred.get().set( imgValue / psiBlurredValue );
		}
	}

	/**
	 * One thread of a method to compute the final values of one iteration of the multiview deconvolution
	 * 
	 * @param start
	 * @param loopSize
	 * @param psi
	 * @param integral
	 * @param weight
	 * @param lambda
	 */
	private static final void computeFinalValues(
			final long start,
			final long loopSize,
			final Img< FloatType > psi,
			final Img< FloatType > integral,
			final Img< FloatType > weight,
			final double lambda )
	{
		final Cursor< FloatType > cursorPsi = psi.cursor();
		final Cursor< FloatType > cursorIntegral = integral.cursor();
		final Cursor< FloatType > cursorWeight = weight.cursor();
		
		cursorPsi.jumpFwd( start );
		cursorIntegral.jumpFwd( start );
		cursorWeight.jumpFwd( start );
		
		for ( long l = 0; l < loopSize; ++l )
		{
			cursorPsi.fwd();
			cursorIntegral.fwd();
			cursorWeight.fwd();

			final float lastPsiValue = cursorPsi.get().get();

			float value = lastPsiValue * cursorIntegral.get().get();

			if ( value > 0 )
			{
				//
				// perform Tikhonov regularization if desired
				//
				if ( lambda > 0 )
					value = ( (float)( (Math.sqrt( 1.0 + 2.0*lambda*value ) - 1.0) / lambda ) );
			}
			else
			{
				value = minValue;
			}

			//
			// get the final value and some statistics
			//
			float nextPsiValue;

			if ( Double.isNaN( value ) )
				nextPsiValue = (float)minValue;
			else
				nextPsiValue = (float)Math.max( minValue, value );

			// compute the difference between old and new
			float change = nextPsiValue - lastPsiValue;

			// apply the appropriate amount
			change *= cursorWeight.get().get();
			nextPsiValue = lastPsiValue + change;

			// store the new value
			cursorPsi.get().set( (float)nextPsiValue );
		}
	}
}
