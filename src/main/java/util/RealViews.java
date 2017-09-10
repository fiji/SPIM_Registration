package util;

import net.imglib2.RealRandomAccessible;

public class RealViews
{
	/**
	 * Add a dimension to a {@link RealRandomAccessible}. The resulting
	 * {@link RealRandomAccessible} has samples from the original dimensions
	 * continuously stacked along the added dimensions.
	 *
	 * The additional dimension is the last dimension. For example, an XYZ view
	 * is created for an XY source. When accessing an XYZ sample in the view,
	 * the final coordinate is discarded and the source XY sample is accessed.
	 *
	 * @param source
	 *            the source
	 * @param <T>
	 *            the pixel type
	 * @return stacked view with an additional last dimension
	 */
	public static < T > RealRandomAccessible< T > addDimension( final RealRandomAccessible< T > source )
	{
		return new StackingRealRandomAccessible< >( source, 1 );
	}
}
