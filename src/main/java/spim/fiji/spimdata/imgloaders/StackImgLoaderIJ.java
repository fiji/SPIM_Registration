package spim.fiji.spimdata.imgloaders;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.Date;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class StackImgLoaderIJ extends StackImgLoader
{
	public StackImgLoaderIJ(
			final File path, final String fileNamePattern, final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final int layoutTP, final int layoutChannels, final int layoutIllum, final int layoutAngles,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		super( path, fileNamePattern, imgFactory, layoutTP, layoutChannels, layoutIllum, layoutAngles, sequenceDescription );
	}

	protected ImagePlus open( File file )
	{
		System.out.println( file );
		
		final ImagePlus imp = new Opener().openImage( file.getAbsolutePath() );

		if ( imp == null )
		{
			IOFunctions.println( "Could not open file with ImageJ TIFF reader: '" + file.getAbsolutePath() + "'" );
			return null;				
		}
		
		return imp;
	}
	
	/**
	 * Get {@link FloatType} image normalized to the range [0,1].
	 *
	 * @param view
	 *            timepoint and setup for which to retrieve the image.
	 * @param normalize
	 * 			  if the image should be normalized to [0,1] or not
	 * @return {@link FloatType} image normalized to range [0,1]
	 */
	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final ViewId view, final boolean normalize )
	{
		final File file = getFile( view );

		if ( file == null )
			throw new RuntimeException( "Could not find file '" + file + "'." );

		final ImagePlus imp = open( file );

		if ( imp == null )
			throw new RuntimeException( "Could not load '" + file + "'." );

		final long[] dim = new long[]{ imp.getWidth(), imp.getHeight(), imp.getNSlices() };
		final Img< FloatType > img = this.instantiateImg( dim, new FloatType() );
		
		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + file + "', most likely out of memory." );
		else
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Opening '" + file + "' [" + dim[ 0 ] + "x" + dim[ 1 ] + "x" + dim[ 2 ] + " image=" + img.getClass().getSimpleName() + "<FloatType>]" );

		final ImageStack stack = imp.getStack();
		final int sizeZ = imp.getNSlices();

		if ( img instanceof ArrayImg || img instanceof PlanarImg )
		{
			final Cursor< FloatType > cursor = img.cursor();
			final int sizeXY = imp.getWidth() * imp.getHeight();

			if ( normalize )
			{
				float min = Float.MAX_VALUE;
				float max = -Float.MAX_VALUE;
				
				for ( int z = 0; z < sizeZ; ++z )
				{				
					final ImageProcessor ip = stack.getProcessor( z + 1 );
	
					for ( int i = 0; i < sizeXY; ++i )
					{
						final float v = ip.getf( i );
						
						if ( v < min )
							min = v;
						
						if ( v > max )
							max = v;
						
						cursor.next().set( v );
					}
				}
				
				for ( final FloatType t : img )
					t.set( ( t.get() - min ) / ( max - min ) );
			}
			else
			{			
				for ( int z = 0; z < sizeZ; ++z )
				{
					final ImageProcessor ip = stack.getProcessor( z + 1 );
	
					for ( int i = 0; i < sizeXY; ++i )
						cursor.next().set( ip.getf( i ) );
				}
			}
		}
		else
		{
			final int width = imp.getWidth();

			if ( normalize )
			{
				float min = Float.MAX_VALUE;
				float max = -Float.MAX_VALUE;
				
				for ( int z = 0; z < sizeZ; ++z )
				{
					final Cursor< FloatType > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();
					final ImageProcessor ip = stack.getProcessor( z + 1 );
					
					while ( cursor.hasNext() )
					{
						cursor.fwd();
						final float v = ip.getf( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width );

						if ( v < min )
							min = v;
						
						if ( v > max )
							max = v;
						
						cursor.get().set( v );
					}
				}
				
				for ( final FloatType t : img )
					t.set( ( t.get() - min ) / ( max - min ) );
			}
			else
			{				
				for ( int z = 0; z < sizeZ; ++z )
				{
					final Cursor< FloatType > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();
					final ImageProcessor ip = stack.getProcessor( z + 1 );
					
					while ( cursor.hasNext() )
					{
						cursor.fwd();
						cursor.get().set( ip.getf( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) );
					}
				}				
			}
		}

		// update the MetaDataCache of the AbstractImgLoader
		// this does not update the XML ViewSetup but has to be called explicitly before saving
		updateMetaDataCache( view, imp.getWidth(), imp.getHeight(), imp.getNSlices(), 
				imp.getCalibration().pixelWidth, imp.getCalibration().pixelHeight, imp.getCalibration().pixelDepth );

		imp.close();
	
		return img;
	}

	/**
	 * Get {@link UnsignedShortType} un-normalized image.
	 *
	 * @param view
	 *            timepoint and setup for which to retrieve the image.
	 * @return {@link UnsignedShortType} image.
	 */
	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		final File file = getFile( view );

		if ( file == null )
			throw new RuntimeException( "Could not find file '" + file + "'." );

		final ImagePlus imp = open( file );
		
		if ( imp == null )
			throw new RuntimeException( "Could not load '" + file + "'." );

		final long[] dim = new long[]{ imp.getWidth(), imp.getHeight(), imp.getNSlices() };
		final Img< UnsignedShortType > img = instantiateImg( dim, new UnsignedShortType() );
		
		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + file + "', most likely out of memory." );
		else
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Opened '" + file + "' [" + dim[ 0 ] + "x" + dim[ 1 ] + "x" + dim[ 2 ] + " image=" + img.getClass().getSimpleName() + "<UnsignedShortType>]" );			

		final ImageStack stack = imp.getStack();
		final int sizeZ = imp.getNSlices();

		if ( img instanceof ArrayImg || img instanceof PlanarImg )
		{
			final Cursor< UnsignedShortType > cursor = img.cursor();
			final int sizeXY = imp.getWidth() * imp.getHeight();
			
			for ( int z = 0; z < sizeZ; ++z )
			{
				final ImageProcessor ip = stack.getProcessor( z + 1 );
				
				for ( int i = 0; i < sizeXY; ++i )
					cursor.next().set( ip.get( i ) );
			}
		}
		else
		{
			final int width = imp.getWidth();
			
			for ( int z = 0; z < sizeZ; ++z )
			{
				final Cursor< UnsignedShortType > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();
				final ImageProcessor ip = stack.getProcessor( z + 1 );
				
				while ( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().set( ip.get( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) );
				}
			}
		}
		
		// update the MetaDataCache of the AbstractImgLoader
		// this does not update the XML ViewSetup but has to be called explicitly before saving
		updateMetaDataCache( view, imp.getWidth(), imp.getHeight(), imp.getNSlices(), 
				imp.getCalibration().pixelWidth, imp.getCalibration().pixelHeight, imp.getCalibration().pixelDepth );

		imp.close();

		return img;
	}

	@Override
	protected void loadMetaData( final ViewId view )
	{
		final File file = getFile( view );
		final ImagePlus imp = open( file );
		
		if ( imp == null )
			throw new RuntimeException( "Could not load '" + file + "'." );
		
		// update the MetaDataCache of the AbstractImgLoader
		// this does not update the XML ViewSetup but has to be called explicitly before saving
		updateMetaDataCache( view, imp.getWidth(), imp.getHeight(), imp.getNSlices(), 
				imp.getCalibration().pixelWidth, imp.getCalibration().pixelHeight, imp.getCalibration().pixelDepth );

		imp.close();
	}
}
