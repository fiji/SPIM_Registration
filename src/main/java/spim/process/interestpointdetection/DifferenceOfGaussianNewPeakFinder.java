package spim.process.interestpointdetection;

import java.util.ArrayList;

import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianReal1;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.InteractiveIntegral;
import mpicbg.spim.segmentation.SimplePeak;

public class DifferenceOfGaussianNewPeakFinder extends DifferenceOfGaussianReal1< FloatType >
{
	ArrayList< SimplePeak > peaks;
	final double min;

	public DifferenceOfGaussianNewPeakFinder( final Image< FloatType> img, OutOfBoundsStrategyFactory< FloatType> outOfBoundsFactory, 
			  final double[] sigma1, final double[] sigma2, double minPeakValue, double normalizationFactor)
	{
		super( img, outOfBoundsFactory, sigma1, sigma2, minPeakValue, normalizationFactor );
		
		this.min = minPeakValue;
	}

	public ArrayList< SimplePeak > getSimplePeaks() { return peaks; }

	@Override
	public ArrayList<DifferenceOfGaussianPeak< FloatType>> findPeaks( final Image< FloatType > laPlace )
	{
		IOFunctions.println( "using new peak finder." );
		peaks = InteractiveIntegral.findPeaks( laPlace, (float)min );

		return new ArrayList<DifferenceOfGaussianPeak< FloatType>>();
	}
}
