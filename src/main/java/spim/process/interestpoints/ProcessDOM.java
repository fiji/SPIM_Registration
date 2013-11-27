package spim.process.interestpoints;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.integer.LongType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.Point;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.DOM;
import mpicbg.spim.segmentation.IntegralImage3d;
import mpicbg.spim.segmentation.InteractiveIntegral;
import mpicbg.spim.segmentation.SimplePeak;
import spim.fiji.plugin.interestpoints.DifferenceOf;

public class ProcessDOM 
{
	public static ArrayList< Point > compute( final Image< FloatType > img, final int radius1, final int radius2, final float threshold, final int localization )
	{
		final Image< LongType > integralImg = IntegralImage3d.compute( img );
		
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

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): min intensity = " + min.get() + ", max intensity = " + max.get() );
		
		// in-place
		final int s1 = radius1*2 + 1;
		final int s2 = radius2*2 + 1;

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Computing Difference-of-Mean");					

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
		
		DOM.computeDifferencOfMean3d( integralImg, domImg, s1, s1, s1, s2, s2, s2, min.get(), max.get() );

		// close integral img
		integralImg.close();
		
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Extracting peaks (radius=" + radius1 + ", threshold=" + threshold + ")");					

		// compute the maxima/minima
		final ArrayList< SimplePeak > peaks = InteractiveIntegral.findPeaks( domImg, threshold );
		final ArrayList< Point > finalPeaks;
		
		if ( localization == 0 )
			finalPeaks = Localization.noLocalization( peaks );
		else if ( localization == 1 )
			finalPeaks = Localization.computeQuadraticLocalization( peaks, domImg );
		else
			finalPeaks = Localization.computeGaussLocalization( peaks, domImg, ( radius2 + radius1 )/2.0 );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Found " + finalPeaks.size() + " peaks." );
		
		return finalPeaks;
	}
}
