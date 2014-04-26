package spim.process.fusion.deconvolution;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class ExtractPSF< T extends RealType< T > >
{
	final ImgFactory< T > psfFactory;
	
	final ArrayList< Img< T > > pointSpreadFunctions, originalPSFs;
	Img< T > avgPSF, avgOriginalPSF;
	
	public ExtractPSF( final ImgFactory< T > psfFactory )
	{		
		this.pointSpreadFunctions = new ArrayList< Img< T > >();
		this.originalPSFs = new ArrayList< Img< T > >();
		
		this.psfFactory = psfFactory;
	}
		
	/**
	 * @return - the extracted PSFs after applying the transformations of each view
	 */
	public ArrayList< Img< T > > getPSFs() { return pointSpreadFunctions; }

	/**
	 * @return - the extracted PSFs in original calibration for each view
	 */
	public ArrayList< Img< T > > getPSFsInInputCalibration() { return originalPSFs; }

	/**
	 * @return - the average extracted PSF after applying the transformations of each view
	 */
	public Img< T > getAveragePSF() { return avgPSF; }
	
	/**
	 * @return - the average extracted PSF in the same calibration as the input data
	 */
	public Img< T > getAverageOriginalPSF() { return avgOriginalPSF; }
	
	/**
	 * Get projection along the smallest dimension (which is usually the rotation axis)
	 * 
	 * @param avgPSF - the average psf
	 * @param minDim - along which dimension to project, if set to <0, the smallest dimension will be chosen
	 * @return - the averaged, projected PSF
	 */
	public Img< T > getMaxProjectionAveragePSF( final Img< T > avgPSF, int minDim )
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
		
		final Img< T > proj = avgPSF.factory().create( projDim, avgPSF.firstElement() );
		
		final RandomAccess< T > psfIterator = avgPSF.randomAccess();
		final Cursor< T > projIterator = proj.localizingCursor();
		
		final int[] tmp = new int[ avgPSF.numDimensions() ];
		
		while ( projIterator.hasNext() )
		{
			projIterator.fwd();

			dim = 0;
			for ( int d = 0; d < dimensions.length; ++d )
				if ( d != minDim )
					tmp[ d ] = projIterator.getIntPosition( dim++ );

			tmp[ minDim ] = -1;
			
			float maxValue = -Float.MAX_VALUE;
			
			psfIterator.setPosition( tmp );
			for ( int i = 0; i < sizeProjection; ++i )
			{
				psfIterator.fwd( minDim );
				final float value = psfIterator.get().getRealFloat();
				
				if ( value > maxValue )
					maxValue = value;
			}
			
			projIterator.get().setReal( maxValue );
		}
		
		return proj;
	}
	
	/**
	 * compute the average psf in original calibration and after applying the transformations
	 * 
	 * @param maxSize
	 */
	public Img< T > computeAverageTransformedPSF( final long[] maxSize, final ArrayList< Img< T > > pointSpreadFunctions )
	{
		final int numDimensions = maxSize.length;
		
		IJ.log( "maxSize: " + Util.printCoordinates( maxSize ) );
		
		Img< T > avgPSF = pointSpreadFunctions.get( 0 ).factory().create( maxSize, pointSpreadFunctions.get( 0 ).firstElement() );
		
		final long[] avgCenter = new long[ numDimensions ];		
		for ( int d = 0; d < numDimensions; ++d )
			avgCenter[ d ] = avgPSF.dimension( d ) / 2;
			
		for ( final Img< T > psf : pointSpreadFunctions )
		{
			final RandomAccess< T > avgCursor = avgPSF.randomAccess();
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
	 * @param originalPSFs
	 * @return
	 */
	public Img< T > computeAveragePSF( final ArrayList< Img< T > > originalPSFs )
	{ 
		final Img< T > avgOriginalPSF = originalPSFs.get( 0 ).factory().create( originalPSFs.get( 0 ), originalPSFs.get( 0 ).firstElement() );

		try
		{		
			for ( final Img< T > psf : originalPSFs )
			{
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
	 * @param psfFactory
	 * @param locations
	 * @param maxSize - will be set
	 */
	public void extractNextImg(
			final Img< T > img,
			final AffineTransform3D model,
			final ImgFactory< T > psfFactory,
			final ArrayList< float[] > locations,
			final long[] psfSize,
			final long[] maxSize )
	{
		final int numDimensions = img.numDimensions();
				
		IOFunctions.println( "PSF size: " + Util.printCoordinates( psfSize ) );
		
		final Img< T > originalPSF = extractPSFLocal( img, psfFactory, locations, psfSize );
		final Img< T > psf = transformPSF( originalPSF, model );
		
		for ( int d = 0; d < numDimensions; ++d )
			if ( psf.dimension( d ) > maxSize[ d ] )
				maxSize[ d ] = psf.dimension( d );
		
		pointSpreadFunctions.add( psf );
		originalPSFs.add( originalPSF );
	}

	
	/**
	 * Transforms the extracted PSF using the affine transformation of the corresponding view
	 * 
	 * @param psf - the extracted psf (NOT z-scaling corrected)
	 * @param model - the transformation model
	 * @return the transformed psf which has odd sizes and where the center of the psf is also the center of the transformed psf
	 */
	protected Img< T > transformPSF( final Img< T > psf, final AffineTransform3D model )
	{
		// here we compute a slightly different transformation than the ImageTransform does
		// two things are necessary:
		// a) the center pixel stays the center pixel
		// b) the transformed psf has a odd size in all dimensions
		
		final int numDimensions = psf.numDimensions();
		
		final RealInterval minMaxDim = model.estimateBounds( psf );
		
		final float[] size = new float[ numDimensions ];		
		final long[] newSize = new long[ numDimensions ];		
		final float[] offset = new float[ numDimensions ];
		
		// the center of the psf has to be the center of the transformed psf as well
		// this is important!
		final float[] center = new float[ numDimensions ]; 
		
		for ( int d = 0; d < numDimensions; ++d )
			center[ d ] = psf.dimension( d ) / 2;
		
		model.apply( center, center );

		for ( int d = 0; d < numDimensions; ++d )
		{						
			size[ d ] = (float)minMaxDim.realMax( d ) -(float) minMaxDim.realMin( d );
			
			newSize[ d ] = (int)size[ d ] + 3;
			if ( newSize[ d ] % 2 == 0 )
				++newSize[ d ];
				
			// the offset is defined like this:
			// the transformed coordinates of the center of the psf
			// are the center of the transformed psf
			offset[ d ] = center[ d ] - newSize[ d ]/2;
			
			//System.out.println( MathLib.printCoordinates( minMaxDim[ d ] ) + " size " + size[ d ] + " newSize " + newSize[ d ] );
		}
		
		return transform( psf, model, newSize, offset );
	}
		
	/**
	 * Extracts the PSF by averaging the local neighborhood RANSAC correspondences
	 * @param view - the SPIM view
	 * @param size - the size in which the psf is extracted (in pixel units, z-scaling is ignored)
	 * @return - the psf, NOT z-scaling corrected
	 */
	protected Img< T > extractPSFLocal(
			final Img< T > img,
			final ImgFactory< T > psfFactory,
			final ArrayList< float[] > locations,
			final long[] size )
	{
		final int numDimensions = size.length;
		
		final Img< T > psf = psfFactory.create( size, img.firstElement() );
		
		// Mirror produces some artifacts ... so we use periodic
		final RealRandomAccess< T > interpolator = 
				Views.interpolate( Views.extendPeriodic( img ), new NLinearInterpolatorFactory< T >() ).realRandomAccess();
		
		final Cursor< T > psfCursor = psf.localizingCursor();
		
		final long[] sizeHalf = size.clone();		
		for ( int d = 0; d < numDimensions; ++d )
			sizeHalf[ d ] /= 2;
		
		final int[] tmpI = new int[ size.length ];
		final float[] tmpF = new float[ size.length ];

		for ( final float[] position : locations )
		{						
			psfCursor.reset();
			
			while ( psfCursor.hasNext() )
			{
				psfCursor.fwd();
				psfCursor.localize( tmpI );

				for ( int d = 0; d < numDimensions; ++d )
					tmpF[ d ] = tmpI[ d ] - sizeHalf[ d ] + position[ d ];
				
				interpolator.setPosition( tmpF );
				
				psfCursor.get().add( interpolator.get() );
			}
		}

		return psf;
	}
	
	public static < T extends RealType< T > > Img< T > transform( final Img< T > image, final AffineTransform3D transform, final long[] newDim, final float[] offset )
	{
		final int numDimensions = image.numDimensions();

		// create the new output image
		final Img< T > transformed = image.factory().create( newDim, Views.iterable( image ).firstElement().createVariable() );

		final Cursor<T> transformedIterator = transformed.localizingCursor();		
		final RealRandomAccess<T> interpolator = Views.interpolate( Views.extendZero( image ), new NLinearInterpolatorFactory<T>() ).realRandomAccess();
		
		final float[] tmp = new float[ numDimensions ];

		while (transformedIterator.hasNext())
		{
			transformedIterator.fwd();

			// we have to add the offset of our new image
			// relative to it's starting point (0,0,0)
			for ( int d = 0; d < numDimensions; ++d )
				tmp[ d ] = transformedIterator.getIntPosition( d ) + offset[ d ];
			
			// transform back into the original image
			// 
			// in order to compute the voxels in the new object we have to apply
			// the inverse transform to all voxels of the new array and interpolate
			// the position in the original image
			transform.applyInverse( tmp, tmp );
			
			interpolator.setPosition( tmp );

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

		float min = Float.MAX_VALUE;

		for ( final T f : img )
			min = Math.min( min, f.getRealFloat() );
		
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
	 * 
	 * @param fileName
	 * @param model - if model is null, PSFs will not be transformed
	 * @return
	 */
	public static < T extends RealType< T > > ExtractPSF< T > loadAndTransformPSFs(
			final ArrayList< File > filenames,
			final ImgFactory< T > factory,
			final T type,
			final AffineTransform3D model )
	{
		final ExtractPSF< T > extractPSF = new ExtractPSF< T >( factory );
		
		final int numDimensions = 3;
				
		final long[] maxSize = new long[ numDimensions ];
		
		for ( int d = 0; d < numDimensions; ++d )
			maxSize[ d ] = 0;

		for ( final File file : filenames )		
		{
	        // extract the PSF for this one	        
    		IOFunctions.println( "Loading PSF file '" + file.getAbsolutePath() );

    		final ImagePlus imp = new Opener().openImage( file.getAbsolutePath() );

    		if ( imp == null )
    			throw new RuntimeException( "Could not load '" + file + "' using ImageJ (should be a TIFF file)." );

    		final ImageStack stack = imp.getStack();
    		final int width = imp.getWidth();
    		final int sizeZ = imp.getNSlices();

    		Img< T > psfImage = factory.create( new long[]{ width, imp.getHeight(), sizeZ }, type );
    		
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

    		final Img< T > psf;
    		
			if ( model != null )
			{
				IOFunctions.println( "Transforming PSF for " + file.getName() );
				psf = extractPSF.transformPSF( psfImage, model );
			}
			else
			{
				IOFunctions.println( "PSF for " + file.getName() + " will not be transformed." );
				psf = psfImage.copy();
			}
						
			for ( int d = 0; d < numDimensions; ++d )
				if ( psf.dimension( d ) > maxSize[ d ] )
					maxSize[ d ] = psf.dimension( d );
			
			extractPSF.pointSpreadFunctions.add( psf );
			extractPSF.originalPSFs.add( psfImage );
		}
		
		extractPSF.computeAverageTransformedPSF( maxSize, extractPSF.pointSpreadFunctions );
		extractPSF.computeAveragePSF( extractPSF.originalPSFs );
		
		return extractPSF;
	}
}
