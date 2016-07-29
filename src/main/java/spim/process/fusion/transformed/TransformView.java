package spim.process.fusion.transformed;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class TransformView
{
	/**
	 * Scale the affine transform and with it the bounding box so it is the right image, but just smaller
	 * 
	 * @param transform
	 * @param boundingBox
	 * @param factor
	 * @return
	 */
	public static Pair< AffineTransform3D, Interval > scale( final AffineTransform3D transform, final FinalInterval boundingBox, final double factor )
	{
		final int n = boundingBox.numDimensions();
		final long[] min = new long[ n ];
		final long[] max = new long[ n ];

		for ( int d = 0; d < min.length; ++ d )
		{
			min[ d ] = Math.round( boundingBox.min( d ) * factor );
			max[ d ] = Math.round( boundingBox.max( d ) * factor );
		}

		final AffineTransform3D t = transform.copy();
		final AffineTransform3D at = new AffineTransform3D();
		at.scale( factor );
		t.preConcatenate( at );

		return new ValuePair< AffineTransform3D, Interval >( t, new FinalInterval( min, max ) );
	}

	/**
	 * Creates a virtual construct that transforms and zero-mins.
	 * 
	 * Note: we do not use a general outofbounds strategy so that when using linear interpolation there are no
	 *       half-correct pixels at the interface between image/background and so that we can clearly know which
	 *       parts are from image data and where there is no data
	 * 
	 * @param input - the input image
	 * @param transform - the affine transformation
	 * @param boundingBox - the bounding box (after transformation)
	 * @param outsideValue - the value that is returned if it does not intersect with the input image
	 * @param interpolation - 0=nearest neighbor, 1=linear interpolation
	 * @return
	 */
	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final RandomAccessibleInterval< T > input,
			final AffineTransform3D transform,
			final FinalInterval boundingBox,
			final float outsideValue,
			final int interpolation )
	{
		final long[] offset = new long[ input.numDimensions() ];
		final long[] size = new long[ input.numDimensions() ];

		for ( int d = 0; d < offset.length; ++d )
		{
			offset[ d ] = boundingBox.min( d );
			size[ d ] = boundingBox.dimension( d );
		}

		final TransformedInputRandomAccessible< T > virtual = new TransformedInputRandomAccessible< T >( input, transform, false, 0.0f, new FloatType( outsideValue ), offset );

		if ( interpolation == 0 )
			virtual.setNearestNeighborInterpolation();
		else
			virtual.setLinearInterpolation();

		return Views.interval( virtual, new FinalInterval( size ) );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final ImgLoader imgloader,
			final ViewId viewId,
			final AffineTransform3D transform,
			final FinalInterval boundingBox,
			final float outsideValue,
			final int interpolation )
	{
		final RandomAccessibleInterval inputImg = imgloader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );

		return transformView( inputImg, transform, boundingBox, outsideValue, interpolation );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final SpimData data,
			final ViewId viewId,
			final FinalInterval boundingBox,
			final float outsideValue,
			final int interpolation )
	{
		final ImgLoader il = data.getSequenceDescription().getImgLoader();
		final ViewRegistration vr = data.getViewRegistrations().getViewRegistration( viewId );
		vr.updateModel();
		
		return transformView( il, viewId, vr.getModel(), boundingBox, outsideValue, interpolation );
	}

	/**
	 * Creates a virtual construct that transforms and zero-mins using a user-defined outofbounds & interpolation.
	 * 
	 * @param input - the input image
	 * @param transform - the affine transformation
	 * @param boundingBox - the bounding box (after transformation)
	 * @param outOfBoundsFactory - outofboundsfactory
	 * @param interpolatorFactory - any interpolatorfactory
	 * @return
	 */
	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final RandomAccessibleInterval< T > input,
			final AffineTransform3D transform,
			final FinalInterval boundingBox,
			final OutOfBoundsFactory< T, RandomAccessible< T > > outOfBoundsFactory,
			final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory )
	{
		final long[] offset = new long[ input.numDimensions() ];
		final long[] size = new long[ input.numDimensions() ];

		for ( int d = 0; d < offset.length; ++d )
		{
			offset[ d ] = boundingBox.min( d );
			size[ d ] = boundingBox.dimension( d );
		}

		final TransformedInputGeneralRandomAccessible< T > virtual = new TransformedInputGeneralRandomAccessible< T >( input, transform, outOfBoundsFactory, interpolatorFactory, offset );

		return Views.interval( virtual, new FinalInterval( size ) );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final ImgLoader imgloader,
			final ViewId viewId,
			final AffineTransform3D transform,
			final FinalInterval boundingBox,
			final OutOfBoundsFactory< T, RandomAccessible< T > > outOfBoundsFactory,
			final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory )
	{
		final RandomAccessibleInterval inputImg = imgloader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );

		return transformView( inputImg, transform, boundingBox, outOfBoundsFactory, interpolatorFactory );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final SpimData data,
			final ViewId viewId,
			final FinalInterval boundingBox,
			final OutOfBoundsFactory< T, RandomAccessible< T > > outOfBoundsFactory,
			final InterpolatorFactory< FloatType, RandomAccessible< FloatType > > interpolatorFactory )
	{
		final ImgLoader il = data.getSequenceDescription().getImgLoader();
		final ViewRegistration vr = data.getViewRegistrations().getViewRegistration( viewId );
		vr.updateModel();
		
		return transformView( il, viewId, vr.getModel(), boundingBox, outOfBoundsFactory, interpolatorFactory );
	}

}
