package spim.process.psf;

import java.util.ArrayList;
import java.util.Collection;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayLocalizingCursor;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import spim.fiji.spimdata.SpimData2;
import spim.process.boundingbox.BoundingBoxReorientation;

public class PSFExtraction< T extends RealType< T > & NativeType< T > >
{
	final ArrayImg< T, ? > psf;

	public PSFExtraction(
			final T type,
			final long[] size )
	{
		psf = new ArrayImgFactory< T >().create( size, type );
	}

	@SuppressWarnings("unchecked")
	public void extractNext( final SpimData2 data, final ViewId viewId, final String label, final boolean useCorresponding )
	{
		final ArrayList< ViewId  > list = new ArrayList< ViewId >();
		list.add( viewId );
		final ArrayList< RealLocalizable > points = BoundingBoxReorientation.extractPoints( label, useCorresponding, false, list, data );
		@SuppressWarnings("rawtypes")
		final RandomAccessibleInterval img = data.getSequenceDescription().getImgLoader().getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );

		extractNext( img, points );
	}

	public void extractNext( final RandomAccessibleInterval< T > img, final Collection< RealLocalizable > locations )
	{
		// Mirror produces some artifacts ... so we use periodic
		extractNext( Views.extendPeriodic( img ), locations );
	}

	public void extractNext( final RandomAccessible< T > img, final Collection< RealLocalizable > locations )
	{
		extractNext( Views.interpolate( img, new NLinearInterpolatorFactory< T >() ), locations );
	}

	public void extractNext( final RealRandomAccessible< T > img, final Collection< RealLocalizable > locations )
	{
		extractPSFLocal( img, locations, psf );
	}

	public ArrayImg< T, ? > getPSF() { return psf; }
	public ArrayImg< T, ? > getTransformedPSF( final AffineTransform3D model )
	{
		final ArrayImg< T, ? > psfCopy = psf.copy();

		// normalize PSF
		normalize( psfCopy );

		return transformPSF( psfCopy, model );
	}

	/**
	 * Extracts the PSF by summing up the local neighborhood of locations (most likely RANSAC correspondences)
	 * @param img - the source image
	 * @param locations - the locations inside the source image
	 * @param the psf in local image coordinates NOT z-scaling corrected
	 */
	public static < T extends RealType< T > > void extractPSFLocal(
			final RealRandomAccessible< T > img,
			final Collection< RealLocalizable > locations,
			final RandomAccessibleInterval< T > psf )
	{
		final int n = img.numDimensions();

		final RealRandomAccess< T > interpolator = img.realRandomAccess();

		final Cursor< T > psfCursor = Views.iterable( Views.zeroMin( psf ) ).localizingCursor();

		final long[] sizeHalf = new long[ n ];
		for ( int d = 0; d < n; ++d )
			sizeHalf[ d ] = psf.dimension( d ) / 2;

		final int[] tmpI = new int[ n ];
		final double[] tmpD = new double[ n ];

		for ( final RealLocalizable position : locations )
		{
			psfCursor.reset();
			
			while ( psfCursor.hasNext() )
			{
				psfCursor.fwd();
				psfCursor.localize( tmpI );

				for ( int d = 0; d < n; ++d )
					tmpD[ d ] = tmpI[ d ] - sizeHalf[ d ] + position.getDoublePosition( d );

				interpolator.setPosition( tmpD );

				psfCursor.get().setReal( psfCursor.get().getRealDouble() + interpolator.get().getRealDouble() );
			}
		}
	}

	/**
	 * Transforms the extracted PSF using the affine transformation of the corresponding view
	 * 
	 * @param psf - the extracted psf (NOT z-scaling corrected)
	 * @param model - the transformation model
	 * @return the transformed psf which has odd sizes and where the center of the psf is also the center of the transformed psf
	 */
	protected static < T extends RealType< T > & NativeType< T > > ArrayImg< T, ? > transformPSF(
			final RandomAccessibleInterval< T > psf,
			final AffineTransform3D model )
	{
		// here we compute a slightly different transformation than the ImageTransform does
		// two things are necessary:
		// a) the center pixel stays the center pixel
		// b) the transformed psf has a odd size in all dimensions
		
		final int numDimensions = psf.numDimensions();
		
		final RealInterval minMaxDim = model.estimateBounds( psf );
		
		final double[] size = new double[ numDimensions ];		
		final long[] newSize = new long[ numDimensions ];		
		final double[] offset = new double[ numDimensions ];
		
		// the center of the psf has to be the center of the transformed psf as well
		// this is important!
		final double[] center = new double[ numDimensions ];
		final double[] tmp = new double[ numDimensions ];

		for ( int d = 0; d < numDimensions; ++d )
			center[ d ] = psf.dimension( d ) / 2;
		
		model.apply( center, tmp );

		for ( int d = 0; d < numDimensions; ++d )
		{
			size[ d ] = minMaxDim.realMax( d ) - minMaxDim.realMin( d );
			
			newSize[ d ] = (int)size[ d ] + 1;
			if ( newSize[ d ] % 2 == 0 )
				++newSize[ d ];
				
			// the offset is defined like this:
			// the transformed coordinates of the center of the psf
			// are the center of the transformed psf
			offset[ d ] = tmp[ d ] - newSize[ d ]/2;
		}
		
		return transform( psf, model, newSize, offset );
	}

	public static < T extends RealType< T > & NativeType< T > > ArrayImg< T, ? > transform(
			final RandomAccessibleInterval< T > image,
			final AffineTransform3D transformIn,
			final long[] newDim,
			final double[] offset )
	{
		final int numDimensions = image.numDimensions();
		final AffineTransform3D transform = transformIn.inverse(); 

		// create the new output image
		final ArrayImg< T, ? > transformed = new ArrayImgFactory< T >().create( newDim, Views.iterable( image ).firstElement() );

		final ArrayLocalizingCursor<T> transformedIterator = transformed.localizingCursor();
		final RealRandomAccess<T> interpolator = Views.interpolate( Views.extendZero( image ), new NLinearInterpolatorFactory<T>() ).realRandomAccess();
		
		final double[] tmp1 = new double[ numDimensions ];
		final double[] tmp2 = new double[ numDimensions ];

		while (transformedIterator.hasNext())
		{
			transformedIterator.fwd();

			// we have to add the offset of our new image
			// relative to it's starting point (0,0,0)
			for ( int d = 0; d < numDimensions; ++d )
				tmp1[ d ] = transformedIterator.getIntPosition( d ) + offset[ d ];
			
			// transform back into the original image
			// 
			// in order to compute the voxels in the new object we have to apply
			// the inverse transform to all voxels of the new array and interpolate
			// the position in the original image
			transform.apply( tmp1, tmp2 );
			
			interpolator.setPosition( tmp2 );

			transformedIterator.get().set( interpolator.get() );
		}

		return transformed;
	}

	private static < T extends RealType< T > >void normalize( final IterableInterval< T > img )
	{
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		for ( final T t : img )
		{
			final double v = t.getRealDouble();
			
			if ( v < min )
				min = v;
			
			if ( v > max )
				max = v;
		}

		for ( final T t : img )
			t.setReal( ( t.getRealDouble() - min ) / ( max - min ) );
	}
}
