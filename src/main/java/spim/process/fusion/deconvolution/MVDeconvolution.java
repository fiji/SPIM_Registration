package spim.process.fusion.deconvolution;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import spim.Threads;
import spim.fiji.ImgLib2Temp.Triple;
import spim.fiji.spimdata.imgloaders.LegacyStackImgLoaderIJ;
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
	public static boolean setBackgroundToAvg = true;//false;
	final static float minValueImg = 1f; // mininal value for the input image (as it is not normalized)
	final static private float minValue = 0.0001f; // minimal value for the deconvolved image

	final static private boolean debugHeavy = true;
	final static private int debugHeavyView = -1;
	final static private int debugHeavyIteration = 0;

	final int numViews, numDimensions;
	final double lambda;

	ImageStack stack;
	CompositeImage ci;

	boolean collectStatistics = true;

	// max intensities for each contributing view
	final float[] max;

	// current iteration
	int i = 0;

	// the multi-view deconvolved image
	Img< FloatType > psi;

	// temporary images that are reused for computation
	final Img< FloatType > tmp1, tmp2;

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
			final String name ) throws IncompatibleTypeException
	{
		this.psi = null;
		this.name = name;
		this.data = views.getViews();
		this.views = views;
		this.numViews = data.size();
		this.numDimensions = data.get( 0 ).getImage().numDimensions();
		this.lambda = lambda;
		this.max = new float[ numViews ];

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Deconvolved & temporary image factory: " + views.imgFactory().getClass().getSimpleName() );

		//for ( final MVDeconFFT m : data )
		//	new DisplayImage().exportImage( m.getImage(), "input" );

		// init all views
		views.init( iterationType );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusing image for first iteration" );

		this.psi = views.imgFactory().create( data.get( 0 ).getImage(), new FloatType() );

		double avg = fuseFirstIteration( psi, views.getViews(), max );

		double avgMax = 0;
		for ( int i = 0; i < max.length; ++i )
		{
			avgMax += max[ i ];
			IOFunctions.println( "Max intensity in overlapping area of view " + i + ": " + max[ i ] );
		}
		avgMax /= (double)max.length;

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Average intensity in overlapping area: " + avg );

		
		if ( initialImage != null )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading intial image '" + initialImage + "'" );
			this.psi = loadInitialImage(
					initialImage,
					checkNumbers,
					minValue,
					data.get( 0 ).getImage(),
					views.imgFactory() );
		}
		else
		{
			if ( Double.isNaN( avg ) )
			{
				avg = 0.5;
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): ERROR! Computing average FAILED, is NaN, setting it to: " + avg );
			}
	
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting image to average intensity: " + avg );
	
			for ( final FloatType t : psi )
				t.set( (float)avg );
		}

		// instantiate the temporary images
		this.tmp1 = views.imgFactory().create( psi, new FloatType() );
		this.tmp2 = views.imgFactory().create( psi, new FloatType() );

		// run the deconvolution
		while ( i < numIterations )
		{
			// show the fused image first
			if ( debug && (i-1) % debugInterval == 0 )
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

			runIteration();
		}

		IOFunctions.println( "Masking never updated pixels." );
		maskNeverUpdatedPixels( tmp1, views.getViews() );

		final Cursor< FloatType > tmp1c = tmp1.cursor();

		for ( final FloatType t : psi )
			if ( tmp1c.next().get() == 0 )
				t.set( 0 );

		IOFunctions.println( "DONE (" + new Date(System.currentTimeMillis()) + ")." );
	}

	protected static final void maskNeverUpdatedPixels( final Img< FloatType > psi, final ArrayList< MVDeconFFT > views )
	{
		fuseFirstIteration( psi, views, null );
	}

	protected static final double fuseFirstIteration( final Img< FloatType > psi, final ArrayList< MVDeconFFT > views, final float[] max )
	{
		final int nThreads = Threads.numThreads();
		final int nPortions = nThreads * 2;

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( psi.size(), nPortions );
		final ArrayList< Callable< Triple< RealSum, Long, float[] > > > tasks = new ArrayList< Callable< Triple< RealSum, Long, float[] > > >();

		final ExecutorService taskExecutor = Executors.newFixedThreadPool( nThreads );

		final ArrayList< RandomAccessibleInterval< FloatType > > imgs = new ArrayList< RandomAccessibleInterval< FloatType > >();

		for ( final MVDeconFFT mvdecon : views )
			imgs.add( mvdecon.getImage() );

		for ( final ImagePortion portion : portions )
			tasks.add( new FirstIteration( portion, psi, imgs ) );

		final RealSum s = new RealSum();
		long count = 0;

		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< Triple< RealSum, Long, float[] > > > imgIntensities = taskExecutor.invokeAll( tasks );

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

		taskExecutor.shutdown();

		return s.getSum() / (double)count;
	}

	protected static Img< FloatType > loadInitialImage(
			final String fileName,
			final boolean checkNumbers,
			final float minValue,
			final Dimensions dimensions,
			final ImgFactory< FloatType > imageFactory )
	{
		IOFunctions.println( "Loading image '" + fileName + "' as start for iteration." );

		final ImagePlus impPSI = LegacyStackImgLoaderIJ.open( new File( fileName ) );

		if ( impPSI == null )
		{
			IOFunctions.println( "Could not load image '" + fileName + "'." );
			return null;
		}

		final long[] dimPsi = impPSI.getStack().getSize() == 1 ? 
				new long[]{ impPSI.getWidth(), impPSI.getHeight() } : new long[]{ impPSI.getWidth(), impPSI.getHeight(), impPSI.getStack().getSize() };
		final Img< FloatType > psi = imageFactory.create( dimPsi, new FloatType() );
		LegacyStackImgLoaderIJ.imagePlus2ImgLib2Img( impPSI, psi, false );

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

	public Img< FloatType > getPsi() { return psi; }	
	public int getCurrentIteration() { return i; }

	public void runIteration()
	{
		runIteration( psi, tmp1, tmp2, data, lambda, max, minValue, collectStatistics, i++ );
	}

	final private static void runIteration(
			final Img< FloatType > psi,
			final Img< FloatType > tmp1, // a temporary image using the same ImgFactory as PSI
			final Img< FloatType > tmp2, // a temporary image using the same ImgFactory as PSI
			final ArrayList< MVDeconFFT > data,
			final double lambda,
			final float[] maxIntensities,
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

		for ( int v = 0; v < numViews; ++v )
		{
			final int view = v;

			final MVDeconFFT processingData = data.get( view );

			//
			// convolve psi (current guess of the image) with the PSF of the current view
			// [psi >> tmp1]
			//

			if ( debugHeavy && ( view == debugHeavyView || debugHeavyView < 0 ) && iteration == debugHeavyIteration)
				new DisplayImage().exportImage( processingData.getImage(), "input " + view );

			if ( debugHeavy && ( view == debugHeavyView || debugHeavyView < 0 ) && iteration == debugHeavyIteration)
				new DisplayImage().exportImage( psi, "psi " + view );

			processingData.convolve1( psi, tmp1 );

			if ( debugHeavy && ( view == debugHeavyView || debugHeavyView < 0 ) && iteration == debugHeavyIteration)
				new DisplayImage().exportImage( tmp1, "psi blurred " + view );

			//
			// compute quotient img/psiBlurred
			// [tmp1, img >> tmp1]
			//
			for ( final ImagePortion portion : portions )
			{
				tasks.add( new Callable< Void >()
				{
					@Override
					public Void call() throws Exception
					{
						computeQuotient( portion.getStartPosition(), portion.getLoopSize(), tmp1, processingData.getImage() );
						return null;
					}
				});
			}

			FusionHelper.execTasks( tasks, nThreads, "compute quotient" );

			if ( debugHeavy && ( view == debugHeavyView || debugHeavyView < 0 ) && iteration == debugHeavyIteration)
				new DisplayImage().exportImage( tmp1, "quotient " + view );

			//
			// blur the residuals image with the kernel
			// (this cannot be don in-place as it might be computed in blocks sequentially,
			// and the input for the n+1'th block cannot be formed by the written back output
			// of the n'th block)
			// [tmp1 >> tmp2]
			//
			processingData.convolve2( tmp1, tmp2 );

			if ( debugHeavy && ( view == debugHeavyView || debugHeavyView < 0 ) && iteration == debugHeavyIteration)
				new DisplayImage().exportImage( tmp2, "quotient blurred " + view );

			//
			// compute final values
			// [psi, weights, tmp2 >> psi]
			//
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
						computeFinalValues( portion.getStartPosition(), portion.getLoopSize(), psi, tmp2, processingData.getWeight(), lambda, minValue, maxIntensities[ view ], sumMax[ portionId ] );
						return null;
					}
				});
			}

			FusionHelper.execTasks( tasks, nThreads, "compute final values " + view );

			// accumulate the results from the individual threads
			double sumChange = 0;
			double maxChange = -1;

			for ( int i = 0; i < nPortions; ++i )
			{
				sumChange += sumMax[ i ][ 0 ];
				maxChange = Math.max( maxChange, sumMax[ i ][ 1 ] );
			}

			IOFunctions.println( "iteration: " + iteration + ", view: " + view + " --- sum change: " + sumChange + " --- max change per pixel: " + maxChange );

			if ( debugHeavy && ( view == debugHeavyView || debugHeavyView < 0 ) && iteration == debugHeavyIteration)
				new DisplayImage().exportImage( processingData.getWeight(), "weight " + view );
			if ( debugHeavy && ( view == debugHeavyView || debugHeavyView < 0 ) && iteration == debugHeavyIteration)
				new DisplayImage().exportImage( psi, "psi new " + view );
		}

		if ( debugHeavy && iteration == debugHeavyIteration )
			SimpleMultiThreading.threadHaltUnClean();
	}

	/**
	 * One thread of a method to compute the quotient between two images of the multiview deconvolution
	 * 
	 * @param start
	 * @param loopSize
	 * @param psiBlurred
	 * @param observedImg
	 */
	private static final void computeQuotient(
			final long start,
			final long loopSize,
			final RandomAccessibleInterval< FloatType > psiBlurred,
			final RandomAccessibleInterval< FloatType > observedImg )
	{
		final IterableInterval< FloatType > psiBlurredIterable = Views.iterable( psiBlurred );
		final IterableInterval< FloatType > observedImgIterable = Views.iterable( observedImg );

		if ( psiBlurredIterable.iterationOrder().equals( observedImgIterable.iterationOrder() ) )
		{
			final Cursor< FloatType > cursorPsiBlurred = psiBlurredIterable.cursor();
			final Cursor< FloatType > cursorImg = observedImgIterable.cursor();
	
			cursorPsiBlurred.jumpFwd( start );
			cursorImg.jumpFwd( start );
	
			for ( long l = 0; l < loopSize; ++l )
			{
				cursorPsiBlurred.fwd();
				cursorImg.fwd();
	
				final float psiBlurredValue = cursorPsiBlurred.get().get();
				final float imgValue = cursorImg.get().get();

				if ( imgValue > 0 )
					cursorPsiBlurred.get().set( imgValue / psiBlurredValue );
				else
					cursorPsiBlurred.get().set( 1 ); // no image data, quotient=1
			}
		}
		else
		{
			final RandomAccess< FloatType > raPsiBlurred = psiBlurred.randomAccess();
			final Cursor< FloatType > cursorImg = observedImgIterable.localizingCursor();

			cursorImg.jumpFwd( start );

			for ( long l = 0; l < loopSize; ++l )
			{
				cursorImg.fwd();
				raPsiBlurred.setPosition( cursorImg );
	
				final float psiBlurredValue = raPsiBlurred.get().get();
				final float imgValue = cursorImg.get().get();
	
				if ( imgValue > 0 )
					raPsiBlurred.get().set( imgValue / psiBlurredValue );
				else
					raPsiBlurred.get().set( 1 ); // no image data, quotient=1
			}
		}
	}

	/**
	 * One thread of a method to compute the final values of one iteration of the multiview deconvolution
	 * 
	 */
	private static final void computeFinalValues(
			final long start,
			final long loopSize,
			final RandomAccessibleInterval< FloatType > psi,
			final RandomAccessibleInterval< FloatType > integral,
			final RandomAccessibleInterval< FloatType > weight,
			final double lambda,
			final float minIntensity,
			final float maxIntensity,
			final double[] sumMax )
	{
		double sumChange = 0;
		double maxChange = -1;

		final IterableInterval< FloatType > psiIterable = Views.iterable( psi );
		final IterableInterval< FloatType > integralIterable = Views.iterable( integral );
		final IterableInterval< FloatType > weightIterable = Views.iterable( weight );

		if (
			psiIterable.iterationOrder().equals( integralIterable.iterationOrder() ) && 
			psiIterable.iterationOrder().equals( weightIterable.iterationOrder() ) )
		{
			final Cursor< FloatType > cursorPsi = psiIterable.cursor();
			final Cursor< FloatType > cursorIntegral = integralIterable.cursor();
			final Cursor< FloatType > cursorWeight = weightIterable.cursor();

			cursorPsi.jumpFwd( start );
			cursorIntegral.jumpFwd( start );
			cursorWeight.jumpFwd( start );

			for ( long l = 0; l < loopSize; ++l )
			{
				cursorPsi.fwd();
				cursorIntegral.fwd();
				cursorWeight.fwd();
	
				// get the final value
				final float lastPsiValue = cursorPsi.get().get();
				final float nextPsiValue = computeNextValue( lastPsiValue, cursorIntegral.get().get(), cursorWeight.get().get(), lambda, minIntensity, maxIntensity );
				
				// store the new value
				cursorPsi.get().set( (float)nextPsiValue );

				// statistics
				final float change = change( lastPsiValue, nextPsiValue );
				sumChange += change;
				maxChange = Math.max( maxChange, change );
			}
		}
		else
		{
			final Cursor< FloatType > cursorPsi = psiIterable.localizingCursor();
			final RandomAccess< FloatType > raIntegral = integral.randomAccess();
			final RandomAccess< FloatType > raWeight = weight.randomAccess();

			cursorPsi.jumpFwd( start );

			for ( long l = 0; l < loopSize; ++l )
			{
				cursorPsi.fwd();
				raIntegral.setPosition( cursorPsi );
				raWeight.setPosition( cursorPsi );

				// get the final value
				final float lastPsiValue = cursorPsi.get().get();
				float nextPsiValue = computeNextValue( lastPsiValue, raIntegral.get().get(), raWeight.get().get(), lambda, minIntensity, maxIntensity );

				// store the new value
				cursorPsi.get().set( (float)nextPsiValue );

				// statistics
				final float change = change( lastPsiValue, nextPsiValue );
				sumChange += change;
				maxChange = Math.max( maxChange, change );
			}
		}

		sumMax[ 0 ] = sumChange;
		sumMax[ 1 ] = maxChange;
	}

	private static final float change( final float lastPsiValue, final float nextPsiValue ) { return Math.abs( ( nextPsiValue - lastPsiValue ) ); }

	/**
	 * compute the next value for a specific pixel
	 * 
	 * @param lastPsiValue - the previous value
	 * @param integralValue - result from the integral
	 * @param lambda - if > 0, regularization
	 * @param minIntensity - the lowest allowed value
	 * @param maxIntensity - to normalize lambda (works between 0...1)
	 * @return
	 */
	private static final float computeNextValue(
			final float lastPsiValue,
			final float integralValue,
			final float weight,
			final double lambda,
			final float minIntensity,
			final float maxIntensity )
	{
		final float value = lastPsiValue * integralValue;
		final float adjustedValue;

		if ( value > 0 )
		{
			//
			// perform Tikhonov regularization if desired
			//
			if ( lambda > 0 )
				adjustedValue = (float)tikhonov( value / maxIntensity, lambda ) * maxIntensity;
			else
				adjustedValue = value;
		}
		else
		{
			adjustedValue = minIntensity;
		}

		//
		// get the final value and some statistics
		//
		final float nextPsiValue;

		if ( Double.isNaN( adjustedValue ) )
			nextPsiValue = (float)minIntensity;
		else
			nextPsiValue = (float)Math.max( minIntensity, adjustedValue );

		// compute the difference between old and new and apply the appropriate amount
		return lastPsiValue + ( ( nextPsiValue - lastPsiValue ) * weight );
	}

	private static final double tikhonov( final double value, final double lambda ) { return ( Math.sqrt( 1.0 + 2.0*lambda*value ) - 1.0 ) / lambda; }

	public static void main( String[] args )
	{
		for ( double d = 0; d < 10; d = d + 0.1 )
		{
			System.out.println( d*10000 + ": " + tikhonov( d*10000, 0.0006 ) );
			System.out.println( d + ": " + tikhonov( d, 0.0006 ) );
		}
	}
}
