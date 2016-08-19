package spim.process.fusion.transformed;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
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
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class TransformView
{
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
			final Interval boundingBox,
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
			final Interval boundingBox,
			final float outsideValue,
			final int interpolation )
	{
		final RandomAccessibleInterval inputImg = openDownsampled( imgloader, viewId, transform );//imgloader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );

		return transformView( inputImg, transform, boundingBox, outsideValue, interpolation );
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< FloatType > transformView(
			final SpimData data,
			final ViewId viewId,
			final Interval boundingBox,
			final float outsideValue,
			final int interpolation )
	{
		final ImgLoader il = data.getSequenceDescription().getImgLoader();
		final ViewRegistration vr = data.getViewRegistrations().getViewRegistration( viewId );
		vr.updateModel();
		
		return transformView( il, viewId, vr.getModel(), boundingBox, outsideValue, interpolation );
	}

	/**
	 * Creates a virtual construct that transforms and zero-mins using a user-defined outofbounds &amp; interpolation.
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
			final Interval boundingBox,
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
		final RandomAccessibleInterval inputImg = openDownsampled( imgloader, viewId, transform );//imgloader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );

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

	private static double length( final double[] a, final double[] b )
	{
		double l = 0;

		for ( int j = 0; j < a.length; ++j )
			l += ( a[ j ] - b[ j ] ) * ( a[ j ] - b[ j ] );

		return Math.sqrt( l );
	}

	private static float[] getStepSize( final AffineTransform3D model )
	{
		final float[] size = new float[ 3 ];

		final double[] tmp = new double[ 3 ];
		final double[] o0 = new double[ 3 ];

		model.apply( tmp, o0 );

		for ( int d = 0; d < 3; ++d )
		{
			final double[] o1 = new double[ 3 ];

			for ( int i = 0; i < tmp.length; ++i )
				tmp[ i ] = 0;

			tmp[ d ] = 1;

			model.apply( tmp, o1 );
			
			size[ d ] = (float)length( o1, o0 );
		}

		return size;
	}

	/**
	 * Opens the image at an appropriate resolution and concatenates an extra transform 
	 * @param imgLoader
	 * @param viewId
	 * @param m
	 * @return
	 */
	public static RandomAccessibleInterval openDownsampled( final ImgLoader imgLoader, final ViewId viewId, final AffineTransform3D m )
	{
		// have to go from input to output
		// https://github.com/bigdataviewer/bigdataviewer-core/blob/master/src/main/java/bdv/util/MipmapTransforms.java

		if ( MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			final MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader )imgLoader;
			final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getMipmapResolutions();

			// best possible step size in the output image when using original data
			final float[] sizeMaxResolution = getStepSize( m );

			System.out.println( Util.printCoordinates( sizeMaxResolution ) );
			float acceptedError = 0.02f;

			// assuming that this is the best one
			int bestLevel = 0;
			double bestScaling = 0;

			// find the best level
			for ( int level = 0; level < mipmapResolutions.length; ++level )
			{
				final double[] factors = mipmapResolutions[ level ];

				final AffineTransform3D s = new AffineTransform3D();
				s.set(
					factors[ 0 ], 0.0, 0.0, 0.0,
					0.0, factors[ 1 ], 0.0, 0.0,
					0.0, 0.0, factors[ 2 ], 0.0 );
	
				System.out.println( "testing scale: " + s );
	
				AffineTransform3D model = m.copy();
				model.concatenate( s );

				final float[] size = getStepSize( model );

				boolean isValid = true;
				
				for ( int d = 0; d < 3; ++d )
					if ( !( size[ d ] < 1.0 + acceptedError || Util.isApproxEqual( size[ d ], sizeMaxResolution[ d ], acceptedError ) ) )
						isValid = false;

				if ( isValid )
				{
					final double totalScale = factors[ 0 ] * factors[ 1 ] * factors[ 2 ];
					
					if ( totalScale > bestScaling )
					{
						bestScaling = totalScale;
						bestLevel = level;
					}
				}
				System.out.println( Util.printCoordinates( size ) + " valid: " + isValid + " bestScaling: " + bestScaling  );
			}

			// concatenate the downsampling transformation model to the affine transform
			m.concatenate( mrImgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );

			System.out.println( "Choosing resolution level: " + mipmapResolutions[ bestLevel ][ 0 ] + " x " + mipmapResolutions[ bestLevel ][ 1 ] + " x " + mipmapResolutions[ bestLevel ][ 2 ] );

			return mrImgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId(), bestLevel );
		}
		else
		{
			return imgLoader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );
		}
	}

}
