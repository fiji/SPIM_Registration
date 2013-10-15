package fiji.spimdata.imgloaders;

import fiji.datasetmanager.StackListLOCI;
import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;

import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
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
			throw new RuntimeException( "Could not load '" + file + "': " + e );
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

	protected < T extends RealType< T > & NativeType< T > > Img< T > openLOCIFloatType( final File path, final T type, final ViewDescription<?, ?> view )
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
				System.out.println( "This is not a two-dimensional file: '" + path + "'" );
				imp2d.close();
				return null;
			}
			
			System.out.println( "Opening '" + path + "' [" + imp2d.getWidth() + "x" + imp2d.getHeight() + "x" + depth + " type=" + 
								imp2d.getProcessor().getClass().getSimpleName() + " image=Img<" + type.getClass().getSimpleName() + ">]" );

			final Img< T > output = instantiateImg( new long[] { imp2d.getWidth(), imp2d.getHeight(), depth }, type ); 
			
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
		
		try 
		{
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
			
			int t = 0;
			int c = 0;
			
			if ( timepoints > 1 )
			{
				System.out.println( "StackImgLoaderLOCI.openLOCI(): File has more than one timepoint, trying to open the right one (by name functioning as id): " + 
									((TimePoint)view.getTimePoint()).getName() );
				t = Integer.parseInt( ((TimePoint)view.getTimePoint()).getName() );
			}
			
			if ( channels > 1 )
			{
				System.out.println( "StackImgLoaderLOCI.openLOCI(): File has more than one channel, trying to open the right one (by name functioning as id): " + 
									((ViewSetup)view.getViewSetup()).getChannel() );
				c = ((ViewSetup)view.getViewSetup()).getChannel();
			}
			
			if (!(pixelType == FormatTools.UINT8 || pixelType == FormatTools.UINT16 || pixelType == FormatTools.UINT32 || pixelType == FormatTools.FLOAT))
			{
				System.out.println( "StackImgLoaderLOCI.openLOCI(): PixelType " + pixelTypeString + " not supported by " + 
									type.getClass().getSimpleName() + ", returning. ");
				return null;
			}

			final Img< T > img;		
			
			img = instantiateImg( new long[] { width, height, depth }, type );

			if ( img == null )
			{
				System.out.println("StackImgLoaderLOCI.openLOCI():  - Could not create image.");
				return null;
			}
			else
			{
				System.out.println( "Opening '" + path + "' [" + width + "x" + height + "x" + depth + " type=" + pixelTypeString + 
									" image=Img<" + type.getClass().getSimpleName() + ">]" );
			}
					
			final byte[][] b = new byte[channels][width * height * bytesPerPixel];
			
			final int planeX = 0;
			final int planeY = 1;
									
			for ( int z = 0; z < depth; ++z )
			{	
				//System.out.println((z+1) + "/" + (end));				
				final Cursor< T > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();
				
				// read the data from LOCI
				for ( int channel = 0; channel < channels; ++channel )
				{
					final int index = r.getIndex( z, channel, t );
					r.openBytes( index, b[ channel ] );	
				}
				
				// write data for that plane into the Image structure using the cursor
				if ( pixelType == FormatTools.UINT8 )
				{						
					while( cursor.hasNext() )
					{
						cursor.fwd();
						cursor.get().setReal( b[ c ][ cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width ] & 0xff );
					}
					
				}	
				else if ( pixelType == FormatTools.UINT16 )
				{
					while( cursor.hasNext() )
					{
						cursor.fwd();
						cursor.get().setReal( getShortValueInt( b[ c ], ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width ) * 2, isLittleEndian ) );
					}
				}						
				else if ( pixelType == FormatTools.INT16 )
				{
					while( cursor.hasNext() )
					{
						cursor.fwd();
						cursor.get().setReal( getShortValue( b[ c ], ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width ) * 2, isLittleEndian ) );
					}
				}						
				else if ( pixelType == FormatTools.UINT32 )
				{
					//TODO: Untested
					while( cursor.hasNext() )
					{
						cursor.fwd();
						cursor.get().setReal( getIntValue( b[ c ], ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width )*4, isLittleEndian ) );
					}
				}
				else if ( pixelType == FormatTools.FLOAT )
				{
					while( cursor.hasNext() )
					{
						cursor.fwd();
						cursor.get().setReal( getFloatValue( b[ c ], ( cursor.getIntPosition( planeX )+ cursor.getIntPosition( planeY )*width )*4, isLittleEndian ) );
					}
				}
			}				
			
			return img;			
			
		}
		catch (IOException exc) { exc.printStackTrace(); System.out.println("StackImgLoaderLOCI.openLOCI(): Sorry, an error occurred: " + exc.getMessage()); return null;}
		catch (FormatException exc) { exc.printStackTrace(); System.out.println("StackImgLoaderLOCI.openLOCI(): Sorry, an error occurred: " + exc.getMessage()); return null;}		
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
