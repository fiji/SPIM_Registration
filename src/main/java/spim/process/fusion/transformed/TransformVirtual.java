package spim.process.fusion.transformed;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;

public class TransformVirtual
{
	/**
	 * Scale the affine transform (use with scaleBoundingBox so it is the right image, but just smaller)
	 * 
	 * @param transform
	 * @param factor
	 * @return
	 */
	public static void scaleTransform( final AffineTransform3D t, final double factor )
	{
		final AffineTransform3D at = new AffineTransform3D();
		at.scale( factor );
		t.preConcatenate( at );
	}

	/**
	 * Scale the bounding box (use with scaleTransform so it is the right image, but just smaller)
	 * 
	 * @param boundingBox
	 * @param factor
	 * @return
	 */
	public static Interval scaleBoundingBox( final Interval boundingBox, final double factor )
	{
		final int n = boundingBox.numDimensions();
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];

		for ( int d = 0; d < min.length; ++ d )
		{
			min[ d ] = Math.round( boundingBox.min( d ) * factor );
			max[ d ] = Math.round( boundingBox.max( d ) * factor );
		}

		return new FinalInterval( min, max );
	}
}
