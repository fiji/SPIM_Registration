package spim.fiji.spimdata;

import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;

/**
 * @author Tobias Pietzsch <tobias.pietzsch@gmail.com>
 */
public class DimensionsUtil
{
	// TODO: This should move to net.imglib2.util.Util or net.imglib2.util.Intervals
	public static long[] toLongArray( final Dimensions dimensions )
	{
		final long[] dims = new long[ dimensions.numDimensions() ];
		dimensions.dimensions( dims );
		return dims;
	}

	// TODO: This should move to net.imglib2.util.Util or net.imglib2.util.Intervals
	public static int[] toIntArray( final Dimensions dimensions )
	{
		final int n = dimensions.numDimensions();
		final int[] dims = new int[ n ];
		for ( int d = 0; d < n; ++d )
			dims[ d ] = ( int ) dimensions.dimension( d );
		return dims;
	}

	public static double[] toLongArray( final VoxelDimensions dimensions )
	{
		final double[] dims = new double[ dimensions.numDimensions() ];
		dimensions.dimensions( dims );
		return dims;
	}
}
