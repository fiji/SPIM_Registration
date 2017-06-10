package spim.process.psf;

import java.util.Collection;
import java.util.List;

import ij.IJ;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class PSFCombination
{
	public static < T extends RealType< T > > Img< T > computeMaxAverageTransformedPSF(
			final Collection< RandomAccessibleInterval< T > > imgs,
			final ImgFactory< T > imgFactory )
	{
		final Img< T > avg = computeAverageImage( imgs, imgFactory );

		int minDim = -1;
		long minDimSize = Long.MAX_VALUE;

		for ( int d = 0; d < avg.numDimensions(); ++d )
			if ( avg.dimension( d ) < minDimSize )
			{
				minDimSize = avg.dimension( d );
				minDim = d;
			}

		return computeMaxProjection( avg, minDim );
	}

	/**
	 * compute the average psf in original calibration and after applying the transformations
	 */
	public static < T extends RealType< T > > Img< T > computeAverageImage(
			final Collection< RandomAccessibleInterval< T > > imgs,
			final ImgFactory< T > imgFactory )
	{
		final long[] maxSize = computeMaxDimTransformedPSF( imgs );
		
		final int numDimensions = maxSize.length;
		
		IJ.log( "maxSize: " + Util.printCoordinates( maxSize ) );

		Img< T > avgPSF = imgFactory.create( maxSize, Views.iterable( imgs.iterator().next() ).firstElement() );

		final long[] avgCenter = new long[ numDimensions ];
		for ( int d = 0; d < numDimensions; ++d )
			avgCenter[ d ] = avgPSF.dimension( d ) / 2;

		for ( final RandomAccessibleInterval< T > psfIn : imgs )
		{
			final IterableInterval< T > psf;

			if ( Views.isZeroMin( psfIn ) )
				psf = Views.iterable( psfIn );
			else
				psf = Views.iterable( Views.zeroMin( psfIn ) );

			// works if the kernel is even
			final RandomAccess< T > avgCursor = Views.extendZero( avgPSF ).randomAccess();
			final Cursor< T > psfCursor = psf.localizingCursor();
			
			final long[] loc = new long[ numDimensions ];
			final long[] psfCenter = new long[ numDimensions ];
			for ( int d = 0; d < numDimensions; ++d )
				psfCenter[ d ] = psf.dimension( d ) / 2;

			while ( psfCursor.hasNext() )
			{
				psfCursor.fwd();
				psfCursor.localize( loc );

				for ( int d = 0; d < numDimensions; ++d )
					loc[ d ] = psfCenter[ d ] - loc[ d ] + avgCenter[ d ];

				avgCursor.setPosition( loc );
				avgCursor.get().add( psfCursor.get() );
			}
		}
		
		return avgPSF;
	}

	/**
	 * @return - maximal dimensions of the transformed PSFs
	 */
	public static long[] computeMaxDimTransformedPSF( final Collection< ? extends Interval > imgs )
	{
		final int numDimensions = 3;
		
		final long[] maxSize = new long[ numDimensions ];

		for ( final Interval img : imgs )
		{
			for ( int d = 0; d < numDimensions; ++d )
				maxSize[ d ] = Math.max( maxSize[ d ], img.dimension( d ) );
		}

		return maxSize;
	}

	/**
	 * Make image the same size as defined, center it
	 * 
	 * @param img
	 * @return
	 */
	public static < T extends RealType< T > > Img< T > makeSameSize( final Img< T > img, final long[] sizeIn )
	{
		final long[] size = sizeIn.clone();

		double min = Double.MAX_VALUE;

		for ( final T f : img )
			min = Math.min( min, f.getRealDouble() );

		final Img< T > square = img.factory().create( size, img.firstElement() );

		final Cursor< T > squareCursor = square.localizingCursor();
		final T minT = img.firstElement().createVariable();
		minT.setReal( min );

		final RandomAccess< T > inputCursor = Views.extendValue( img, minT ).randomAccess();

		while ( squareCursor.hasNext() )
		{
			squareCursor.fwd();
			squareCursor.localize( size );
			
			for ( int d = 0; d < img.numDimensions(); ++d )
				size[ d ] =  size[ d ] - square.dimension( d )/2 + img.dimension( d )/2;

			inputCursor.setPosition( size );
			squareCursor.get().set( inputCursor.get() );
		}
		
		return square;
	}

	/**
	 * Get projection along the smallest dimension (which is usually the rotation axis)
	 * 
	 * @param avgPSF - the average psf
	 * @param minDim - along which dimension to project, if set to &lt;0, the smallest dimension will be chosen
	 * @return - the averaged, projected PSF
	 */
	public static < S extends RealType< S > > Img< S > computeMaxProjection( final Img< S > avgPSF, int minDim )
	{
		return computeMaxProjection( avgPSF, avgPSF.factory(), minDim );
	}

	public static < S extends RealType< S > > Img< S > computeMaxProjection( final RandomAccessibleInterval< S > avgPSF, final ImgFactory< S > factory, int minDim )
	{
		final long[] dimensions = new long[ avgPSF.numDimensions() ];
		avgPSF.dimensions( dimensions );
		
		if ( minDim < 0 )
		{
			long minSize = dimensions[ 0 ];
			minDim = 0;
			
			for ( int d = 0; d < dimensions.length; ++d )
			{
				if ( avgPSF.dimension( d ) < minSize )
				{
					minSize = avgPSF.dimension( d );
					minDim = d;
				}
			}
		}
		
		final long[] projDim = new long[ dimensions.length - 1 ];
		
		int dim = 0;
		long sizeProjection = 0;
		
		// the new dimensions
		for ( int d = 0; d < dimensions.length; ++d )
			if ( d != minDim )
				projDim[ dim++ ] = dimensions[ d ];
			else
				sizeProjection = dimensions[ d ];
		
		final Img< S > proj = factory.create( projDim, Views.iterable( avgPSF ).firstElement() );
		
		final RandomAccess< S > psfIterator = avgPSF.randomAccess();
		final Cursor< S > projIterator = proj.localizingCursor();
		
		final int[] tmp = new int[ avgPSF.numDimensions() ];
		
		while ( projIterator.hasNext() )
		{
			projIterator.fwd();

			dim = 0;
			for ( int d = 0; d < dimensions.length; ++d )
				if ( d != minDim )
					tmp[ d ] = projIterator.getIntPosition( dim++ );

			tmp[ minDim ] = -1;
			
			double maxValue = -Double.MAX_VALUE;
			
			psfIterator.setPosition( tmp );
			for ( int i = 0; i < sizeProjection; ++i )
			{
				psfIterator.fwd( minDim );
				final double value = psfIterator.get().getRealDouble();
				
				if ( value > maxValue )
					maxValue = value;
			}
			
			projIterator.get().setReal( maxValue );
		}
		
		return proj;
	}

	/**
	 * Returns the bounding box so that all images can fit in there
	 * or null if input is null or input.size is 0
	 * 
	 * @param images
	 * @return
	 */
	public static < T extends Type< T > > long[] commonSize( final List< Img< T > > images )
	{
		if ( images == null || images.size() == 0 )
			return null;
		
		final long[] size = new long[ images.get( 0 ).numDimensions() ]; 
		images.get( 0 ).dimensions( size );
		
		for ( final Img< T > image : images )
			for ( int d = 0; d < image.numDimensions(); ++d )
				size[ d ] = Math.max( size[ d ], image.dimension( d ) );
		
		return size;
	}
}
