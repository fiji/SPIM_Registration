package spim.process.interestpointdetection;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.integer.LongType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.DOM;
import mpicbg.spim.segmentation.IntegralImage3d;
import mpicbg.spim.segmentation.InteractiveIntegral;
import mpicbg.spim.segmentation.SimplePeak;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public class ProcessDOM 
{
	public static ArrayList< InterestPoint > compute( 
			final Image< FloatType > img, 
			final int radius1, 
			final int radius2, 
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
		final Image< LongType > integralImg = IntegralImage3d.compute( img );
		
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

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): min intensity = " + min.get() + ", max intensity = " + max.get() );
		
		// in-place
		final int sX1 = Math.max( 3, (int)Math.round( radius1 * (0.5/imageSigmaX ) ) * 2 + 1 );
		final int sX2 = Math.max( 5, (int)Math.round( radius2 * (0.5/imageSigmaX ) ) * 2 + 1 );

		final int sY1 = Math.max( 3, (int)Math.round( radius1 * (0.5/imageSigmaY ) ) * 2 + 1 );
		final int sY2 = Math.max( 5, (int)Math.round( radius2 * (0.5/imageSigmaY ) ) * 2 + 1 );

		final int sZ1 = Math.max( 3, (int)Math.round( radius1 * (0.5/imageSigmaZ ) ) * 2 + 1 );
		final int sZ2 = Math.max( 5, (int)Math.round( radius2 * (0.5/imageSigmaZ ) ) * 2 + 1 );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Computing Difference-of-Mean, diameters = (" + sX1 + ", "  + sX2 + ", "  + sY1 + ", "  + sY2 + ", "  + sZ1 + ", "  + sZ2 + ")" );

		// in-place overwriting img if no adjacent Gauss fit is required
		final Image< FloatType > domImg;
		
		if ( localization == 2 )
		{
			domImg = img.createNewImage();
		}
		else
		{
			domImg = img;
			for ( final FloatType tt : img )
				tt.setZero();
		}
		
		DOM.computeDifferencOfMean3d( integralImg, domImg, sX1, sY1, sZ1, sX2, sY2, sZ2, min.get(), max.get() );

		// close integral img
		integralImg.close();
		
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Extracting peaks (radius=" + radius1 + ", threshold=" + threshold + ")");					

		// compute the maxima/minima
		final ArrayList< SimplePeak > peaks = InteractiveIntegral.findPeaks( domImg, threshold );
		final ArrayList< InterestPoint > finalPeaks;
		
		if ( localization == 0 )
			finalPeaks = Localization.noLocalization( peaks, findMin, findMax );
		else if ( localization == 1 )
			finalPeaks = Localization.computeQuadraticLocalization( peaks, domImg, findMin, findMax, threshold );
		else
			finalPeaks = Localization.computeGaussLocalization( peaks, domImg, ( radius2 + radius1 )/2.0, findMin, findMax, threshold );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Found " + finalPeaks.size() + " peaks." );
		
		return finalPeaks;
	}
}
