package spim.fiji;

import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;

public class ImgLib2Temp
{
	// TODO: Remove when Imglib2 is updated in Fiji
	public static FinalInterval getIntervalFromDimension( final Dimensions dim )
	{
		final long[] min = new long[ dim.numDimensions() ];
		final long[] max = new long[ dim.numDimensions() ];
		for ( int d = 0; d < dim.numDimensions(); ++d )
		{
			min[ d ] = 0;
			max[ d ] = dim.dimension( d ) - 1;
		}

		return new FinalInterval( min, max );
	}
}
