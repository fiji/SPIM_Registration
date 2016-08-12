package spim.process.fusion.deconvolution.normalize;

import java.util.List;
import java.util.concurrent.Callable;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import spim.process.fusion.ImagePortion;

public class WeightNormalizerConstant extends WeightNormalizer
{
	public WeightNormalizerConstant( final List< RandomAccessibleInterval< FloatType > > weights )
	{
		super( weights );
	}

	@Override
	public void adjustForOSEM( final double osemspeedup )
	{
		for ( final RandomAccessibleInterval< FloatType > w : weights )
		{
			final RandomAccess< FloatType > r = w.randomAccess();
			final long[] min = new long[ w.numDimensions() ];
			w.min( min );
			r.setPosition( min );
			r.get().set( (float)Math.min( 1.0, ( 1.0 / (double)weights.size() ) * osemspeedup ) ); // individual contribution never higher than 1
		}
	}

	@Override
	protected Callable<double[]> createWeightAdjustmentThread(ImagePortion portion, boolean additionalSmoothBlending, float maxDiffRange, float scalingRange) { return null; }

	@Override
	protected void postProcess() {}
	
	@Override
	public boolean process() { return true; }

	@Override
	public int getMinOverlappingViews() { return weights.size(); }

	@Override
	public double getAvgOverlappingViews() { return weights.size(); }
}
