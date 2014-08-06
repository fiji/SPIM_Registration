package spim.process.interestpointdetection;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianReal1;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.registration.bead.laplace.LaPlaceFunctions;
import mpicbg.spim.segmentation.DOM;
import mpicbg.spim.segmentation.SimplePeak;
import net.imglib2.util.Util;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public class ProcessDOG
{
	public static ArrayList< InterestPoint > compute( 
			final Image< FloatType > img, 
			final float sigma, 
			final float threshold, 
			final int localization,
			final double imageSigmaX,
			final double imageSigmaY,
			final double imageSigmaZ,
			final boolean findMin, 
			final boolean findMax,
			final double minIntensity,
			final double maxIntensity )
	{
		float initialSigma = sigma;
		
		final float minPeakValue = threshold;
		final float minInitialPeakValue;
		
		if ( localization == 0 )
			minInitialPeakValue = minPeakValue;
		else
			minInitialPeakValue = threshold/10.0f;

		final FloatType min = new FloatType();
		final FloatType max = new FloatType();
		
		if ( Double.isNaN( minIntensity ) || Double.isNaN( maxIntensity ) || minIntensity == maxIntensity )
		{
			DOM.computeMinMax( img, min, max );
		}
		else
		{
			min.set( (float)minIntensity );
			max.set( (float)maxIntensity );
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): min intensity = " + min.get() + ", max intensity = " + max.get() );

		// normalize image
		final float diff = max.get() - min.get();
		final float minValue = min.get();

		for ( final FloatType f : img )
			f.set( (f.get() - minValue) / diff );

		final float k = LaPlaceFunctions.computeK( 4 );
		final float K_MIN1_INV = LaPlaceFunctions.computeKWeight(k);
		final int steps = 3;
		
		//
		// Compute the Sigmas for the gaussian convolution
		//
		final float[] sigmaStepsX = LaPlaceFunctions.computeSigma( steps, k, initialSigma );
		final float[] sigmaStepsDiffX = LaPlaceFunctions.computeSigmaDiff( sigmaStepsX, (float)imageSigmaX );
		
		final float[] sigmaStepsY = LaPlaceFunctions.computeSigma( steps, k, initialSigma );
		final float[] sigmaStepsDiffY = LaPlaceFunctions.computeSigmaDiff( sigmaStepsY, (float)imageSigmaY );
		
		final float[] sigmaStepsZ = LaPlaceFunctions.computeSigma( steps, k, initialSigma );
		final float[] sigmaStepsDiffZ = LaPlaceFunctions.computeSigmaDiff( sigmaStepsZ, (float)imageSigmaZ );
		
		final double[] sigma1 = new double[]{ sigmaStepsDiffX[0], sigmaStepsDiffY[0], sigmaStepsDiffZ[0] };
		final double[] sigma2 = new double[]{ sigmaStepsDiffX[1], sigmaStepsDiffY[1], sigmaStepsDiffZ[1] };

		// compute difference of gaussian
		final DifferenceOfGaussianReal1<FloatType> dog = new DifferenceOfGaussianReal1<FloatType>( img, new OutOfBoundsStrategyMirrorFactory<FloatType>(), sigma1, sigma2, minInitialPeakValue, K_MIN1_INV );
		
		// do quadratic fit??
		if ( localization == 1 )
			dog.setKeepDoGImage( true );
		else
			dog.setKeepDoGImage( false );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): computing difference-of-gausian (sigma=" + initialSigma + ", " +
				"threshold=" + minPeakValue + ", sigma1=" + Util.printCoordinates( sigma1 ) + ", sigma2=" + Util.printCoordinates( sigma2 ) + ")" );
		
		dog.process();
		
		//ImageJFunctions.copyToImagePlus( dog.getDoGImage() ).show();
		
		final ArrayList< DifferenceOfGaussianPeak<FloatType> > peakListOld = dog.getPeaks();
		final ArrayList< SimplePeak > peaks = new ArrayList< SimplePeak >();
		final int n = img.getNumDimensions();

		for ( final DifferenceOfGaussianPeak<FloatType> peak : peakListOld )
		{
			if ( peak.isValid() )
			{
				final int[] location = new int[ n ];

				for ( int d = 0; d < n; ++d )
					location[ d ] = peak.getPosition( d );

				peaks.add( new SimplePeak( location, peak.getValue().get(), peak.isMin(), peak.isMax() ) );
			}
		}

		final ArrayList< InterestPoint > finalPeaks;

		if ( localization == 0 )
		{
			finalPeaks = Localization.noLocalization( peaks, findMin, findMax );
		}
		else if ( localization == 1 )
		{
			finalPeaks = Localization.computeQuadraticLocalization( peaks, dog.getDoGImage(), findMin, findMax, minPeakValue );
			dog.getDoGImage().close();
		}
		else
		{
			finalPeaks = Localization.computeGaussLocalization( peaks, img, sigma, findMin, findMax, minPeakValue );
		}
		
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Found " + finalPeaks.size() + " peaks." );

		return finalPeaks;
	}
}
