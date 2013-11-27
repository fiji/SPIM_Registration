package spim.process.interestpoints;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianReal1;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyMirrorFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.Point;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.registration.bead.laplace.LaPlaceFunctions;
import mpicbg.spim.segmentation.DOM;
import mpicbg.spim.segmentation.SimplePeak;
import spim.fiji.plugin.interestpoints.DifferenceOf;

public class ProcessDOG
{
	public static ArrayList< Point > compute( 
			final Image< FloatType > img, 
			final float sigma, 
			final float threshold, 
			final int localization,
			final boolean findMin, 
			final boolean findMax )
	{
        float imageSigma = 0.5f;
        float initialSigma = sigma;

        final float minPeakValue = threshold;
        final float minInitialPeakValue = threshold/10.0f;

		final FloatType min = new FloatType();
		final FloatType max = new FloatType();
		
		if ( DifferenceOf.minmaxset == null )
		{
			DOM.computeMinMax( img, min, max );
		}
		else
		{
			min.set( DifferenceOf.minmaxset[ 0 ] );
			max.set( DifferenceOf.minmaxset[ 1 ] );
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
        final float[] sigmaSteps = LaPlaceFunctions.computeSigma( steps, k, initialSigma );
        final float[] sigmaStepsDiff = LaPlaceFunctions.computeSigmaDiff( sigmaSteps, imageSigma );
         
		// compute difference of gaussian
		final DifferenceOfGaussianReal1<FloatType> dog = new DifferenceOfGaussianReal1<FloatType>( img, new OutOfBoundsStrategyMirrorFactory<FloatType>(), sigmaStepsDiff[0], sigmaStepsDiff[1], minInitialPeakValue, K_MIN1_INV );
		
		// do quadratic fit??
		if ( localization == 1 )
			dog.setKeepDoGImage( true );
		else
			dog.setKeepDoGImage( false );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): computing difference-of-gausian (sigma=" + initialSigma + ", threshold=" + minPeakValue + ")" );
		dog.process();
		
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

		final ArrayList< Point > finalPeaks;

		if ( localization == 0 )
		{
			finalPeaks = Localization.noLocalization( peaks, findMin, findMax );
		}
		else if ( localization == 1 )
		{
			finalPeaks = Localization.computeQuadraticLocalization( peaks, dog.getDoGImage(), findMin, findMax );
			dog.getDoGImage().close();
		}
		else
		{
			finalPeaks = Localization.computeGaussLocalization( peaks, img, sigma, findMin, findMax );
		}
		
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Found " + finalPeaks.size() + " peaks." );

		return finalPeaks;
	}
}
