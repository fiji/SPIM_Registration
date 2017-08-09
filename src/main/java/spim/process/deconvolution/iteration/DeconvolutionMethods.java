package spim.process.deconvolution.iteration;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class DeconvolutionMethods
{
	/**
	 * One thread of a method to compute the quotient between two images of the multiview deconvolution
	 * 
	 * @param start - the start position in pixels for this thread
	 * @param loopSize - how many consecutive pixels to process
	 * @param psiBlurred - the blurred psi input
	 * @param observedImg - the observed image
	 */
	protected static final void computeQuotient(
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

	/*
	 * One thread of a method to compute the final values of one iteration of the multiview deconvolution
	 */
	protected static final void computeFinalValues(
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

}