package spim.fiji.plugin.resave;

import fiji.util.gui.GenericDialogPlus;
import ij.plugin.PlugIn;

import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.datasetmanager.StackList;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.imgloaders.StackImgLoaderIJ;
import spim.process.fusion.export.Save3dTIFF;
import bdv.export.ProgressWriter;

public class Resave_TIFF implements PlugIn
{
	public static String defaultPath = "";
	public static int defaultContainer = 0;
	
	public static void main( final String[] args )
	{
		new Resave_TIFF().run( null );
	}
	
	public static class Parameters
	{
		ImgFactory< ? extends NativeType< ? > > imgFactory;
		String xmlFile;
	}

	@Override
	public void run( final String arg0 )
	{
		final LoadParseQueryXML lpq = new LoadParseQueryXML();

		if ( !lpq.queryXML() )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );
		
		final Parameters params = getParameters();

		// write the TIFF's
		writeTIFF( lpq, new File( params.xmlFile ).getParent(), progressWriter );

		// copy the interest points if they exist
		copyInterestPoints( lpq.getData().getBasePath(), new File( params.xmlFile ).getParentFile() );
		
		// write the XML
		try
		{
			writeXML( lpq, params, progressWriter );
		}
		catch (SpimDataException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void copyInterestPoints( final File srcBase, final File destBase )
	{
		final File src = new File( srcBase, "interestpoints" );
		
		if ( src.exists() )
		{
			final File target = new File( destBase, "interestpoints" );
			
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Interestpoint directory exists. Copying '" + src + "' >>> '" + target + "'" );
			
			try
			{
				copyFolder( src, target );
			}
			catch (IOException e)
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": FAILED to copying '" + src + "' >>> '" + target + "': " + e );
				e.printStackTrace();
			}
		}
	}

	protected Parameters getParameters()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Resave dataset as TIFF" );

		if ( defaultPath == null || defaultPath.trim().length() == 0 )
			defaultPath = LoadParseQueryXML.defaultXMLfilename;
		
		gd.addFileField( "Select new XML", defaultPath, 80 );
		
		gd.addChoice( "ImgLib2_data_container", StackList.imglib2Container, StackList.imglib2Container[ defaultContainer ] );
		gd.addMessage( "Use ArrayImg if -ALL- input views are smaller than ~2048x2048x500 px (2^31 px), or if the\n" +
					   "program throws an OutOfMemory exception while processing.  CellImg is slower, but more\n" +
				       "memory efficient and supports much larger file sizes only limited by the RAM of the machine.", 
				       new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		final Parameters params = new Parameters();
		
		params.xmlFile = defaultPath = gd.getNextString();
		
		if ( ( defaultContainer = gd.getNextChoiceIndex() ) == 0 )
			params.imgFactory = new ArrayImgFactory< FloatType >();
		else
			params.imgFactory = new CellImgFactory< FloatType >();

		return params;
	}

	public static void writeTIFF( final LoadParseQueryXML lpq, final String path, final ProgressWriter progressWriter )
	{
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving TIFFS to directory '" + path + "'" );
		final Save3dTIFF save = new Save3dTIFF( path );
		
		final int numAngles = lpq.getAnglesToProcess().size();
		final int numChannels = lpq.getChannelsToProcess().size();
		final int numIlluminations = lpq.getIlluminationsToProcess().size();
		final int numTimepoints = lpq.getTimePointsToProcess().size();
		
		final int countImageStacks = lpq.getTimePointsToProcess().size() * lpq.getViewSetupsToProcess().size();
		int i = 0;
		
		for ( final TimePoint t : lpq.getTimePointsToProcess() )
			for ( final ViewSetup v : lpq.getViewSetupsToProcess() )
			{
				i++;
				
				final ViewId viewId = new ViewId( t.getId(), v.getId() );
				
				final ViewDescription viewDescription = lpq.getData().getSequenceDescription().getViewDescription( 
						viewId.getTimePointId(), viewId.getViewSetupId() );

				if ( !viewDescription.isPresent() )
					continue;
				
				final RandomAccessibleInterval img = lpq.getData().getSequenceDescription().getImgLoader().getImage( viewId );
				
				String filename = "img";
				
				if ( numTimepoints > 1 )
					filename += "_TL" + t.getId();
				
				if ( numChannels > 1 )
					filename += "_Ch" + v.getChannel().getName();
				
				if ( numIlluminations > 1 )
					filename += "_Ill" + v.getIllumination().getName();
				
				if ( numAngles > 1 )
					filename += "_Angle" + v.getAngle().getName();
				
				filename += ".tif";
				
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving '" + new File( lpq.getData().getBasePath(), filename ) + "' ..." );
				save.exportImage( img, filename );
				
				progressWriter.setProgress( ((i-1) / (double)countImageStacks) * 99.00  );
			}
	}
	
	public static void writeXML(
			final LoadParseQueryXML lpq,
			final Parameters params,
			final ProgressWriter progressWriter )
		throws SpimDataException
	{
		int layoutTP = 0, layoutChannels = 0, layoutIllum = 0, layoutAngles = 0;
		String filename = "img";

		if ( lpq.getTimePointsToProcess().size() > 1 )
		{
			filename += "_TL{t}";
			layoutTP = 1;
		}
		
		if ( lpq.getChannelsToProcess().size() > 1 )
		{
			filename += "_Ch{c}";
			layoutChannels = 1;
		}
		
		if ( lpq.getIlluminationsToProcess().size() > 1 )
		{
			filename += "_Ill{i}";
			layoutIllum = 1;
		}
		
		if ( lpq.getAnglesToProcess().size() > 1 )
		{
			filename += "_Angle{a}";
			layoutAngles = 1;
		}

		final StackImgLoaderIJ loader = new StackImgLoaderIJ(
				new File( params.xmlFile ).getParentFile(),
				filename, params.imgFactory,
				layoutTP, layoutChannels, layoutIllum, layoutAngles, null );
		lpq.getData().getSequenceDescription().setImgLoader( loader );
		lpq.getData().setBasePath( new File( params.xmlFile ).getParentFile() );

		lpq.getIO().save( lpq.getData(), new File( params.xmlFile ).getAbsolutePath() );
		
		progressWriter.setProgress( 1.0 );
		progressWriter.out().println( "done" );
	}
	
	public static void copyFolder( final File src, final File dest ) throws IOException
	{
		if ( src.isDirectory() )
		{
			if( !dest.exists() )
				dest.mkdir();

			for ( final String file : src.list() )
				copyFolder( new File( src, file ), new File( dest, file ) );

		}
		else
		{
			final InputStream in = new FileInputStream( src );
			final OutputStream out = new FileOutputStream( dest ); 
 
			final byte[] buffer = new byte[ 65535 ];
 
			int length;

			while ( ( length = in.read(buffer) ) > 0 )
				out.write(buffer, 0, length);

 
			in.close();
			out.close();
		}
	}
}
