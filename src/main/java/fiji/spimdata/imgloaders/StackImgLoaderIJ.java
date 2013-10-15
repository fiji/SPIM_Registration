package fiji.spimdata.imgloaders;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.Opener;
import ij.process.ImageProcessor;

import java.io.File;

import mpicbg.spim.data.sequence.ViewDescription;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

public class StackImgLoaderIJ extends StackImgLoader
{
	protected ImagePlus open( File file )
	{
		final ImagePlus imp = new Opener().openImage( file.getAbsolutePath() );

		if ( imp == null )
		{
			IJ.log( "Could not open file with ImageJ TIFF reader: '" + file.getAbsolutePath() + "'" );
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
	public RandomAccessibleInterval< FloatType > getImage( final ViewDescription< ?, ? > view, final boolean normalize )
	{
		final File file = getFile( view );
		final ImagePlus imp = open( file );
		
		if ( imp == null )
			throw new RuntimeException( "Could not load '" + file + "'." );

		final long[] dim = new long[]{ imp.getWidth(), imp.getHeight(), imp.getNSlices() };
		final Img< FloatType > img = this.instantiateImg( dim, new FloatType() );
		
		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + file + "', most likely out of memory." );
		else
			IJ.log( "Opening '" + path + "' [" + dim[ 0 ] + "x" + dim[ 1 ] + "x" + dim[ 2 ] + " image=" + img.getClass().getSimpleName() + "<FloatType>]" );			
			
		final ImageStack stack = imp.getStack();
		final Cursor< FloatType > cursor = img.cursor();
		final int sizeXY = imp.getWidth() * imp.getHeight();
		final int sizeZ = imp.getNSlices();
		
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
				t.set( (t.get() - min) / ( max - min ) );
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
	public RandomAccessibleInterval< UnsignedShortType > getUnsignedShortImage( final ViewDescription< ?, ? > view )
	{
		final File file = getFile( view );
		final ImagePlus imp = open( file );
		
		if ( imp == null )
			throw new RuntimeException( "Could not load '" + file + "'." );

		final long[] dim = new long[]{ imp.getWidth(), imp.getHeight(), imp.getNSlices() };
		final Img< UnsignedShortType > img = instantiateImg( dim, new UnsignedShortType() );
		
		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + file + "', most likely out of memory." );
		else
			IJ.log( "Opening '" + path + "' [" + dim[ 0 ] + "x" + dim[ 1 ] + "x" + dim[ 2 ] + " image=" + img.getClass().getSimpleName() + "<FloatType>]" );			

		final ImageStack stack = imp.getStack();
		final Cursor< UnsignedShortType > cursor = img.cursor();
		final int sizeXY = imp.getWidth() * imp.getHeight();
		final int sizeZ = imp.getNSlices();

		for ( int z = 0; z < sizeZ; ++z )
		{
			final ImageProcessor ip = stack.getProcessor( z + 1 );
			
			for ( int i = 0; i < sizeXY; ++i )
				cursor.next().set( ip.get( i ) );
		}
		
		imp.close();

		return img;
	}
}
