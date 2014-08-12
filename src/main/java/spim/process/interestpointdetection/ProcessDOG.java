package spim.process.interestpointdetection;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianReal1;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.display.imagej.ImageJFunctions;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.registration.bead.laplace.LaPlaceFunctions;
import mpicbg.spim.segmentation.SimplePeak;
import net.imglib2.img.Img;
import net.imglib2.util.Util;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDASeparableConvolution;
import spim.process.fusion.FusionHelper;

public class ProcessDOG
{
	/**
	 * @param deviceList - a list of CUDA capable devices (or null if classic CPU computation in Java)
	 * @param accurateCUDA - use accurate CUDA implementation (including out of bounds or not)
	 * @param img - ImgLib1 image
	 * @param imglib2img - ImgLib2 image (based on same image data as the ImgLib1 image, must be a wrap)
	 * @param sigma
	 * @param threshold
	 * @param localization
	 * @param imageSigmaX
	 * @param imageSigmaY
	 * @param imageSigmaZ
	 * @param findMin
	 * @param findMax
	 * @param minIntensity
	 * @param maxIntensity
	 * @return
	 */
	public static ArrayList< InterestPoint > compute(
			final CUDASeparableConvolution cuda,
			final List< CUDADevice > deviceList,
			final boolean accurateCUDA,
			final Image< FloatType > img,
			final Img< net.imglib2.type.numeric.real.FloatType > imglib2img,
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

		final float min, max;

		if ( Double.isNaN( minIntensity ) || Double.isNaN( maxIntensity ) || minIntensity == maxIntensity )
		{
			final float[] minmax = FusionHelper.minMax( imglib2img );
			min = minmax[ 0 ];
			max = minmax[ 1 ];
		}
		else
		{
			min = (float)minIntensity;
			max = (float)maxIntensity;
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): min intensity = " + min + ", max intensity = " + max );

		// normalize image
		FusionHelper.normalizeImage( imglib2img, min, max );

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
		
		final DifferenceOfGaussianReal1<FloatType> dog;
		
		if ( deviceList == null )
			dog = new DifferenceOfGaussianReal1<FloatType>( img, new OutOfBoundsStrategyMirrorFactory<FloatType>(), sigma1, sigma2, minInitialPeakValue, K_MIN1_INV );
		else if ( accurateCUDA )
			dog = new DifferenceOfGaussianCUDA( cuda, deviceList, img, imglib2img, new OutOfBoundsStrategyMirrorFactory<FloatType>(), sigma1, sigma2, minInitialPeakValue, K_MIN1_INV );
		else
			dog = new DifferenceOfGaussianCUDA( cuda, deviceList, img, imglib2img, sigma1, sigma2, minInitialPeakValue, K_MIN1_INV );
		
		// do quadratic fit??
		if ( localization == 1 )
			dog.setKeepDoGImage( true );
		else
			dog.setKeepDoGImage( false );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): computing difference-of-gausian (sigma=" + initialSigma + ", " +
				"threshold=" + minPeakValue + ", sigma1=" + Util.printCoordinates( sigma1 ) + ", sigma2=" + Util.printCoordinates( sigma2 ) + ")" );
		
		dog.process();

		ImageJFunctions.copyToImagePlus( dog.getDoGImage() ).show();
		
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
			finalPeaks = Localization.computeGaussLocalization( peaks, null, sigma, findMin, findMax, minPeakValue );
		}
		
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Found " + finalPeaks.size() + " peaks." );

		return finalPeaks;
	}
}
