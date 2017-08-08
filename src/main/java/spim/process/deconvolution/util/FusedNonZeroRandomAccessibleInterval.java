package spim.process.deconvolution.util;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.real.FloatType;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;

public class FusedNonZeroRandomAccessibleInterval extends FusedRandomAccessibleInterval
{
	final ArrayList< FusedNonZeroRandomAccess > accesses;

	public FusedNonZeroRandomAccessibleInterval(
			final Interval interval,
			final List< ? extends RandomAccessible< FloatType > > images,
			final List< ? extends RandomAccessible< FloatType > > weights )
	{
		super( interval, images, weights );

		this.accesses = new ArrayList<>();
	}

	public ArrayList< FusedNonZeroRandomAccess > getAllAccesses() { return accesses; }

	@Override
	public RandomAccess< FloatType > randomAccess()
	{
		final FusedNonZeroRandomAccess r = new FusedNonZeroRandomAccess( numDimensions(), getImages(), getWeights() );

		accesses.add( r );

		return r;
	}
}
