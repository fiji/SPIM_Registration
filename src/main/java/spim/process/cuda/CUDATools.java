package spim.process.cuda;

import ij.IJ;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import mpicbg.spim.io.IOFunctions;
import fiji.util.gui.GenericDialogPlus;

public class CUDATools
{
	public static void loadSpecificLibrary( final ArrayList< String > potentialNames )
	{
		try
		{
			// it cannot be null
			if ( System.getProperty( "jna.library.path" ) == null )
				System.setProperty( "jna.library.path", "" );
			
			final GenericDialogPlus gd = new GenericDialogPlus( "Specify path of native library for CUDA" );

			final String fijiDir = IJ.getDirectory( "ImageJ" );

			final File dir = new File( fijiDir );
			final String ext = getLibraryExtension().toLowerCase();

			IOFunctions.println( "Looking for native libraries ending with '" + ext + "' in directory: '" + dir.getAbsolutePath() + "' ... " );

			final String[] libs = dir.list( new FilenameFilter() {
				@Override
				public boolean accept( final File dir, final String name )
				{
					if ( name.toLowerCase().endsWith( ext ) )
						return true;
					else
						return false;
				}
			});

			if ( libs == null || libs.length == 0 )
			{
				IOFunctions.println( "No libraries found." );
				return;
			}

			int index = 0;
			
			for ( int i = 0; i < libs.length; ++i )
				for ( final String s : potentialNames )
					if ( s.contains( libs[ i ] ) )
						index = i;
			
			gd.addMessage( "Fiji directory: '" + fijiDir + "'" );
			gd.addChoice( "Select_library", libs, libs[ index ] );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return;
		}
		catch ( UnsatisfiedLinkError e )
		{
			IOFunctions.println( "Cannot load CUDA JNA library: " + e );
		}
	}

	public static String getLibraryExtension()
	{
		if ( IJ.isWindows() )
			return ".dll";
		else if ( IJ.isLinux() )
			return ".so";
		else if ( IJ.isMacOSX() || IJ.isMacintosh() )
			return ".dylib";
		else
			return "";
	}
	
	public static void main( String[] args )
	{
		
	}
}
