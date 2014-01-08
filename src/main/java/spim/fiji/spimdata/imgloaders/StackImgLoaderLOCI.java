package spim.fiji.spimdata.imgloaders;

import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import spim.fiji.datasetmanager.StackListLOCI;

import loci.formats.ChannelSeparator;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class StackImgLoaderLOCI extends StackImgLoader
{
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
	public RandomAccessibleInterval<FloatType> getImage( final ViewDescription<?, ?> view, final boolean normalize )
	{
		final File file = getFile( view );
		
		try
		{
			final Img< FloatType > img = openLOCIFloatType( file, new FloatType(), view );
			
			if ( img == null )
				throw new RuntimeException( "Could not load '" + file + "'" );
			
			if ( normalize )
			{
				float min = Float.MAX_VALUE;
				float max = -Float.MAX_VALUE;

				for ( final FloatType t : img )
				{
					final float v = t.get();
					
					if ( v < min )
						min = v;
					
					if ( v > max )
						max = v;					
				}
				
				for ( final FloatType t : img )
					t.set( ( t.get() - min ) / ( max - min ) );
	
			}
			
			return img;
		} 
		catch ( Exception e )
		{
			throw new RuntimeException( "Could not load '" + file + "':\n" + e );
		}
	}

	/**
	 * Get {@link UnsignedShortType} un-normalized image.
	 *
	 * @param view
	 *            timepoint and setup for which to retrieve the image.
	 * @return {@link UnsignedShortType} image.
	 */
	@Override
	public RandomAccessibleInterval< UnsignedShortType > getUnsignedShortImage( final ViewDescription<?, ?> view )
	{
		final File file = getFile( view );
		
		try
		{
			final Img< UnsignedShortType > img = openLOCIFloatType( file, new UnsignedShortType(), view );
			
			if ( img == null )
				throw new RuntimeException( "Could not load '" + file + "'" );
			
			return img;
		} 
		catch ( Exception e )
		{
			throw new RuntimeException( "Could not load '" + file + "': " + e );
		}
	}

	protected < T extends RealType< T > & NativeType< T > > Img< T > openLOCIFloatType( final File path, final T type, final ViewDescription<?, ?> view ) throws Exception
	{						
		// read many 2d-images if it is a directory
		if ( path.isDirectory() )
		{
			final String[] files = path.list( new FilenameFilter() 
			{	
				@Override
				public boolean accept( final File dir, final String name) 
				{
					final File newFile = new File( dir, name );
					
					// ignore directories and hidden files
					if ( newFile.isHidden() || newFile.isDirectory() )
						return false;
					else
						return true;
				}
			});
			Arrays.sort( files );
			final int depth = files.length;
			
			// get size of first image
			final Opener io = new Opener();
			ImagePlus imp2d = io.openImage( path.getAbsolutePath() + File.separator + files[ 0 ] );

			if ( imp2d.getStack().getSize() > 1 )
			{
				IOFunctions.println( "This is not a two-dimensional file: '" + path + "'" );
				imp2d.close();
				return null;
			}
			
			final Img< T > output = instantiateImg( new long[] { imp2d.getWidth(), imp2d.getHeight(), depth }, type ); 
			
			if ( output == null )
				throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + path + "', most likely out of memory." );
			
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Opening '" + path + "' [" + imp2d.getWidth() + "x" + imp2d.getHeight() + "x" + depth + " type=" + 
					imp2d.getProcessor().getClass().getSimpleName() + " image=" + output.getClass().getSimpleName() + "<" + type.getClass().getSimpleName() + ">]" );

			for ( int z = 0; z < depth; ++z )
			{
				imp2d = io.openImage( path.getAbsolutePath() + File.separator + files[ z ] );
				final ImageProcessor ip = imp2d.getProcessor();
				
				final Cursor< T > cursor = Views.iterable( Views.hyperSlice( output, 2, z ) ).localizingCursor();
								
				while ( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( ip.getPixelValue( cursor.getIntPosition( 0 ), cursor.getIntPosition( 1 ) ) );
				}
			}			
			return output;
		}

		final IFormatReader r = new ChannelSeparator();

		if ( !StackListLOCI.createOMEXMLMetadata( r ) )
		{
			try
			{
				r.close();
			} 
			catch (IOException e)
			{
				e.printStackTrace();
			}
			return null;
		}
		
		final String id = path.getAbsolutePath();
		
		r.setId( id );
					
		final boolean isLittleEndian = r.isLittleEndian();			
		final int width = r.getSizeX();
		final int height = r.getSizeY();
		final int depth = r.getSizeZ();				
		int timepoints = r.getSizeT();
		int channels = r.getSizeC();
		final int pixelType = r.getPixelType();
		final int bytesPerPixel = FormatTools.getBytesPerPixel( pixelType ); 
		final String pixelTypeString = FormatTools.getPixelTypeString( pixelType );
		
		// which channel and timepoint to load from this file
		int t = 0;
		int c = 0;
		
		if ( layoutTP == 2 )
		{
			t = Integer.parseInt( ((TimePoint)view.getTimePoint()).getName() );
			
			if ( t >= timepoints )
				throw new RuntimeException( "File '" + path + "' has only timepoints [0 ... " + (timepoints-1) + "], but you want to open timepoint " + t + ". Stopping.");
		}
		
		if ( layoutChannels == 2 )
		{ 
			c = Integer.parseInt( ((ViewSetup)view.getViewSetup()).getChannel().getName() );
			
			if ( c >= channels )
				throw new RuntimeException( "File '" + path + "' has only channels [0 ... " + (channels-1) + "], but you want to open channel " + c + ". Stopping.");
		}
		
		if (!(pixelType == FormatTools.UINT8 || pixelType == FormatTools.UINT16 || pixelType == FormatTools.UINT32 || pixelType == FormatTools.FLOAT))
		{
			IOFunctions.println( "StackImgLoaderLOCI.openLOCI(): PixelType " + pixelTypeString + " not supported by " + 
					type.getClass().getSimpleName() + ", returning. ");
			return null;
		}

		final Img< T > img;		
		
		img = instantiateImg( new long[] { width, height, depth }, type );

		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + path + "', most likely out of memory." );
		else
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Opening '" + path + "' [" + width + "x" + height + "x" + depth + " ch=" + c + " tp=" + t + " type=" + pixelTypeString + " image=" + img.getClass().getSimpleName() + "<" + type.getClass().getSimpleName() + ">]" );
				
		final byte[] b = new byte[width * height * bytesPerPixel];
		
		final int planeX = 0;
		final int planeY = 1;
								
		for ( int z = 0; z < depth; ++z )
		{	
			final Cursor< T > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();
			
			r.openBytes( r.getIndex( z, c, t ), b );	
			
			if ( pixelType == FormatTools.UINT8 )
			{						
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( b[ cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width ] & 0xff );
				}					
			}	
			else if ( pixelType == FormatTools.UINT16 )
			{
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( getShortValueInt( b, ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width ) * 2, isLittleEndian ) );
				}
			}						
			else if ( pixelType == FormatTools.INT16 )
			{
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( getShortValue( b, ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width ) * 2, isLittleEndian ) );
				}
			}						
			else if ( pixelType == FormatTools.UINT32 )
			{
				//TODO: Untested
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( getIntValue( b, ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width )*4, isLittleEndian ) );
				}
			}
			else if ( pixelType == FormatTools.FLOAT )
			{
				while( cursor.hasNext() )
				{
					cursor.fwd();
					cursor.get().setReal( getFloatValue( b, ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width )*4, isLittleEndian ) );
				}
			}
		}				
		
		return img;			
	}

	private static final float getFloatValue( final byte[] b, final int i, final boolean isLittleEndian )
	{
		if ( isLittleEndian )
			return Float.intBitsToFloat( ((b[i+3] & 0xff) << 24)  + ((b[i+2] & 0xff) << 16)  +  ((b[i+1] & 0xff) << 8)  + (b[i] & 0xff) );
		else
			return Float.intBitsToFloat( ((b[i] & 0xff) << 24)  + ((b[i+1] & 0xff) << 16)  +  ((b[i+2] & 0xff) << 8)  + (b[i+3] & 0xff) );
	}

	private static final int getIntValue( final byte[] b, final int i, final boolean isLittleEndian )
	{
		if ( isLittleEndian )
			return ( ((b[i+3] & 0xff) << 24)  + ((b[i+2] & 0xff) << 16)  +  ((b[i+1] & 0xff) << 8)  + (b[i] & 0xff) );
		else
			return ( ((b[i] & 0xff) << 24)  + ((b[i+1] & 0xff) << 16)  +  ((b[i+2] & 0xff) << 8)  + (b[i+3] & 0xff) );
	}
	
	private static final short getShortValue( final byte[] b, final int i, final boolean isLittleEndian )
	{
		return (short)getShortValueInt( b, i, isLittleEndian );
	}

	private static final int getShortValueInt( final byte[] b, final int i, final boolean isLittleEndian )
	{
		if ( isLittleEndian )
			return ((((b[i+1] & 0xff) << 8)) + (b[i] & 0xff));
		else
			return ((((b[i] & 0xff) << 8)) + (b[i+1] & 0xff));
	}

	protected static String checkPath( String path )
	{
		if (path.length() > 1) 
		{
			path = path.replace('\\', '/');
			if (!path.endsWith("/"))
				path = path + "/";
		}
		
		return path;
	}
}
