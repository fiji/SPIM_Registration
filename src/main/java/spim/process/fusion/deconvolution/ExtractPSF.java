package spim.process.fusion.deconvolution;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import spim.fiji.ImgLib2Temp.Pair;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayLocalizingCursor;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class ExtractPSF< T extends RealType< T > & NativeType< T > >
{
	final protected HashMap< ViewId, ArrayImg< T, ? > > pointSpreadFunctions, originalPSFs;
	final protected ArrayList< ViewId > viewIds;

	// if this ExtractPSF instance manages PSF's for other channels, we store it here
	final HashMap< ViewId, ViewId > mapViewIds;

	public ExtractPSF()
	{		
		this.pointSpreadFunctions = new HashMap< ViewId, ArrayImg< T, ? > >();
		this.originalPSFs = new HashMap< ViewId, ArrayImg< T, ? > >();
		this.viewIds = new ArrayList< ViewId >();

		this.mapViewIds = new HashMap< ViewId, ViewId >();
	}

	/**
	 * @return - the current mapping, should be appended by any process that wants to use exisiting PSF's
	 */
	public HashMap< ViewId, ViewId > getViewIdMapping() { return mapViewIds; }

	public HashMap< ViewId, ArrayImg< T, ? > > getPSFMap() { return pointSpreadFunctions; }

	/**
	 * Returns the transformed PSF. It will first try to look it up directly, if not available it will
	 * check the mapping mapViewIds&lt; from, to &gt; which one should be used for this viewid.
	 *
	 * @param viewId
	 * @return - the extracted PSFs after applying the transformations of each view
	 */
	public ArrayImg< T, ? > getTransformedPSF( final ViewId viewId )
	{
		if ( pointSpreadFunctions.containsKey( viewId ) )
			return pointSpreadFunctions.get( viewId );
		else if ( mapViewIds.containsKey( viewId ) )
			return pointSpreadFunctions.get( mapViewIds.get( viewId ) );
		else
			throw new RuntimeException( "Cannot find PSF for tpid: " + viewId.getTimePointId() + ", setupid=" + viewId.getViewSetupId() );
	}

	/**
	 * @return - the extracted PSFs in original calibration for each view
	 */
	public HashMap< ViewId, ArrayImg< T, ? > > getInputCalibrationPSFs() { return originalPSFs; }
	
	/**
	 * @return - the viewdescriptions corresponding to the PSFs
	 */
	public ArrayList< ViewId > getViewIdsForPSFs() { return viewIds; }
	
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
	 * compute the average psf in original calibration and after applying the transformations
	 */
	public Img< T > computeAverageTransformedPSF()
	{
		final long[] maxSize = computeMaxDimTransformedPSF();
		
		final int numDimensions = maxSize.length;
		
		IJ.log( "maxSize: " + Util.printCoordinates( maxSize ) );

		Img< T > someImg = pointSpreadFunctions.values().iterator().next();
		Img< T > avgPSF = someImg.factory().create( maxSize, someImg.firstElement() );
		
		final long[] avgCenter = new long[ numDimensions ];
		for ( int d = 0; d < numDimensions; ++d )
			avgCenter[ d ] = avgPSF.dimension( d ) / 2;

		for ( final ViewId viewId : getViewIdsForPSFs() )
		{
			final Img< T > psf = pointSpreadFunctions.get( viewId );

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
	 * Compute average PSF in local image coordinates, all images are supposed to have the same dimensions
	 * 
	 * @return
	 */
	public Img< T > computeAveragePSF()
	{
		Img< T > someImg = originalPSFs.values().iterator().next();
		final Img< T > avgOriginalPSF = someImg.factory().create( someImg, someImg.firstElement() );

		try
		{
			for ( final ViewId viewId : getViewIdsForPSFs() )
			{
				final Img< T > psf = originalPSFs.get( viewId );

				final Cursor< T > cursor = psf.cursor();

				for ( final T t : avgOriginalPSF )
					t.add( cursor.next() );
			}
		}
		catch (Exception e) 
		{
			IOFunctions.println( "Input PSFs were most likely of different size ... not computing average image in original scale." );
			e.printStackTrace();
		}
		
		return avgOriginalPSF;
	}
	
	/**
	 * 
	 * @param img
	 * @param viewId
	 * @param model
	 * @param locations
	 * @param psfSize - dimensions of psf to extract
	 */
	public void extractNextImg(
			final RandomAccessibleInterval< T > img,
			final ViewId viewId,
			final AffineTransform3D model,
			final ArrayList< double[] > locations,
			final long[] psfSize )
	{
		IOFunctions.println( "PSF size: " + Util.printCoordinates( psfSize ) );

		final ArrayImg< T, ? > originalPSF = extractPSFLocal( img, locations, psfSize );

		// normalize PSF
		normalize( originalPSF );

		final ArrayImg< T, ? > psf = transformPSF( originalPSF, model );

		viewIds.add( viewId );
		pointSpreadFunctions.put( viewId, psf );
		originalPSFs.put( viewId, originalPSF );
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
		
	/**
	 * Extracts the PSF by averaging the local neighborhood RANSAC correspondences
	 * @param size - the size in which the psf is extracted (in pixel units, z-scaling is ignored)
	 * @return - the psf, NOT z-scaling corrected
	 */
	protected static < T extends RealType< T > & NativeType< T > > ArrayImg< T, ? > extractPSFLocal(
			final RandomAccessibleInterval< T > img,
			final ArrayList< double[] > locations,
			final long[] size )
	{
		final int numDimensions = size.length;
		
		final ArrayImg< T, ? > psf = new ArrayImgFactory< T >().create( size, Views.iterable( img ).firstElement() );
		
		// Mirror produces some artifacts ... so we use periodic
		final RealRandomAccess< T > interpolator =
				Views.interpolate( Views.extendPeriodic( img ), new NLinearInterpolatorFactory< T >() ).realRandomAccess();
		
		final ArrayLocalizingCursor< T > psfCursor = psf.localizingCursor();
		
		final long[] sizeHalf = size.clone();
		for ( int d = 0; d < numDimensions; ++d )
			sizeHalf[ d ] /= 2;
		
		final int[] tmpI = new int[ size.length ];
		final double[] tmpD = new double[ size.length ];

		for ( final double[] position : locations )
		{
			psfCursor.reset();
			
			while ( psfCursor.hasNext() )
			{
				psfCursor.fwd();
				psfCursor.localize( tmpI );

				for ( int d = 0; d < numDimensions; ++d )
					tmpD[ d ] = tmpI[ d ] - sizeHalf[ d ] + position[ d ];
				
				interpolator.setPosition( tmpD );
				
				psfCursor.get().add( interpolator.get() );
			}
		}

		return psf;
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
	
	/**
	 * @return - maximal dimensions of the transformed PSFs
	 */
	public long[] computeMaxDimTransformedPSF()
	{
		final int numDimensions = 3;
		
		final long[] maxSize = new long[ numDimensions ];

		for ( final Img< T > transformedPSF : pointSpreadFunctions.values() )
			for ( int d = 0; d < numDimensions; ++d )
				maxSize[ d ] = Math.max( maxSize[ d ], transformedPSF.dimension( d ) );

		return maxSize;
	}
	
	/**
	 * 
	 * @param filenames
	 * @return
	 */
	public static < T extends RealType< T > & NativeType< T > > ExtractPSF< T > loadAndTransformPSFs(
			final ArrayList< Pair< Pair< Angle, Illumination >, String > > filenames,
			final ArrayList< ViewDescription > viewDesc,
			final T type,
			final HashMap< ViewId, AffineTransform3D > models )
	{
		final ExtractPSF< T > extractPSF = new ExtractPSF< T >();

		for ( final ViewDescription vd : viewDesc )
		{
			final File file = getFileNameForViewId( vd, filenames );

			// extract the PSF for this one
			IOFunctions.println( "Loading PSF file '" + file.getAbsolutePath() );

			final ImagePlus imp = new Opener().openImage( file.getAbsolutePath() );
	
			if ( imp == null )
				throw new RuntimeException( "Could not load '" + file + "' using ImageJ (should be a TIFF file)." );
	
			final ImageStack stack = imp.getStack();
			final int width = imp.getWidth();
			final int sizeZ = imp.getNSlices();
	
			ArrayImg< T, ? > psfImage = new ArrayImgFactory< T >().create( new long[]{ width, imp.getHeight(), sizeZ }, type );

			for ( int z = 0; z < sizeZ; ++z )
			{
				final Cursor< T > cursor = Views.iterable( Views.hyperSlice( psfImage, 2, z ) ).localizingCursor();
				final ImageProcessor ip = stack.getProcessor( z + 1 );

				while ( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( ip.getf( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) );
				}
			}

			final ArrayImg< T, ? > psf;

			if ( models != null )
			{
				IOFunctions.println( "Transforming PSF for viewid " + vd.getViewSetupId() + ", file=" + file.getName() );
				psf = ExtractPSF.transformPSF( psfImage, models.get( vd ) );
			}
			else
			{
				IOFunctions.println( "PSF for viewid " + vd.getViewSetupId() + ", file=" + file.getName() + " will not be transformed." );
				psf = psfImage.copy();
			}

			extractPSF.viewIds.add( vd );
			extractPSF.pointSpreadFunctions.put( vd, psf );
			extractPSF.originalPSFs.put( vd, psfImage );
		}
		
		return extractPSF;
	}

	protected static File getFileNameForViewId( final ViewDescription vd, final ArrayList< Pair< Pair< Angle, Illumination >, String > > filenames )
	{
		for ( final Pair< Pair< Angle, Illumination >, String > pair : filenames )
			if ( pair.getA().getA().getId() == vd.getViewSetup().getAngle().getId() && pair.getA().getB().getId() == vd.getViewSetup().getIllumination().getId() )
				return new File( pair.getB() );

		return null;
	}
}
