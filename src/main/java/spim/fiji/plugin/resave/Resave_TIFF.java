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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.TimePointsPattern;
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
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.imgloaders.StackImgLoaderIJ;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.fusion.export.Save3dTIFF;
import bdv.export.ProgressWriter;

public class Resave_TIFF implements PlugIn
{
	public static String defaultPath = "";
	public static int defaultContainer = 0;
	public static boolean defaultCompress = false;

	public static void main( final String[] args )
	{
		new Resave_TIFF().run( null );
	}

	public static class Parameters
	{
		ImgFactory< ? extends NativeType< ? > > imgFactory;
		String xmlFile;
		boolean compress;
	}

	@Override
	public void run( final String arg0 )
	{
		final LoadParseQueryXML lpq = new LoadParseQueryXML();

		if ( !lpq.queryXML( "Resaving as TIFF", "Resave", true, true, true, true ) )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );
		
		final Parameters params = getParameters();
		
		if ( params == null )
			return;

		// write the TIFF's
		writeTIFF( lpq, new File( params.xmlFile ).getParent(), params.compress, progressWriter );

		// write the XML
		try
		{
			final List< String > filesToCopy = writeXML( lpq, params, progressWriter );

			// copy the interest points if they exist
			copyInterestPoints( lpq.getData().getBasePath(), new File( params.xmlFile ).getParentFile(), filesToCopy );
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + params.xmlFile + "'." );
			e.printStackTrace();
		}
		finally
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + params.xmlFile + "'." );
		}
	}

	public static void copyInterestPoints( final File srcBase, final File destBase, final List< String > filesToCopy )
	{
		final File src = new File( srcBase, "interestpoints" );
		
		if ( src.exists() )
		{
			final File target = new File( destBase, "interestpoints" );
			
			if ( src.getAbsolutePath().equals( target.getAbsolutePath() ) )
				return;
			
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Interestpoint directory exists. Copying '" + src + "' >>> '" + target + "'" );
			
			try
			{
				copyFolder( src, target, filesToCopy );
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

		PluginHelper.addSaveAsFileField( gd, "Select new XML", defaultPath, 80 );
		
		gd.addChoice( "ImgLib2_data_container", StackList.imglib2Container, StackList.imglib2Container[ defaultContainer ] );
		gd.addCheckbox( "Lossless compression of TIFF files (ZIP)", defaultCompress );
		gd.addMessage( "Use ArrayImg if -ALL- input views are smaller than ~2048x2048x500 px (2^31 px), or if the\n" +
					   "program throws an OutOfMemory exception while processing.  CellImg is slower, but more\n" +
				       "memory efficient and supports much larger file sizes only limited by the RAM of the machine.", 
				       new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		final Parameters params = new Parameters();
		
		params.xmlFile = gd.getNextString();
		
		if ( !params.xmlFile.endsWith( ".xml" ) )
			params.xmlFile += ".xml";

		params.compress = defaultCompress = gd.getNextBoolean();

		defaultPath = LoadParseQueryXML.defaultXMLfilename = params.xmlFile;

		if ( ( defaultContainer = gd.getNextChoiceIndex() ) == 0 )
			params.imgFactory = new ArrayImgFactory< FloatType >();
		else
			params.imgFactory = new CellImgFactory< FloatType >();

		return params;
	}

	public static void writeTIFF( final LoadParseQueryXML lpq, final String path, final boolean compress, final ProgressWriter progressWriter )
	{
		if ( compress )
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving compressed TIFFS to directory '" + path + "'" );
		else
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving TIFFS to directory '" + path + "'" );
		
		final Save3dTIFF save = new Save3dTIFF( path, compress );
		
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

				save.exportImage( img, filename );

				progressWriter.setProgress( ((i-1) / (double)countImageStacks) * 95.00  );
			}
	}

	/**
	 * Assembles a new SpimData2 based on the subset of timepoints and viewsetups as selected by the user.
	 * The imgloader is still not set here.
	 * 
	 * It also fills up a list of filesToCopy from the interestpoints directory if it is not null.
	 * 
	 * @param lpq
	 * @param params
	 * @param filesToCopy
	 * @return
	 */
	public static SpimData2 assemblePartialSpimData2( final LoadParseQueryXML lpq, final File basePath, final List< String > filesToCopy )
	{
		final TimePoints timepoints;

		try
		{
			timepoints = new TimePointsPattern( listAllTimePoints( lpq.getTimePointsToProcess() ) );
		}
		catch (ParseException e)
		{
			IOFunctions.println( "Automatically created list of timepoints failed to parse. This should not happen, really :) -- " + e );
			IOFunctions.println( "Here is the list: " + listAllTimePoints( lpq.getTimePointsToProcess() ) );
			e.printStackTrace();
			return null;
		}
		
		final List< ViewSetup > setups = lpq.getViewSetupsToProcess();
		final MissingViews missingViews = lpq.getData().getSequenceDescription().getMissingViews();
				
		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, setups, null, missingViews );

		// a hashset for all viewsetups that remain
		final Set< ViewId > views = new HashSet< ViewId >();
		
		for ( final TimePoint t : lpq.getTimePointsToProcess() )
			for ( final ViewSetup v : lpq.getViewSetupsToProcess() )
				views.add( new ViewId( t.getId(), v.getId() ) );

		// re-assemble the registrations
		final Map< ViewId, ViewRegistration > oldRegMap = lpq.getData().getViewRegistrations().getViewRegistrations();
		final Map< ViewId, ViewRegistration > newRegMap = new HashMap< ViewId, ViewRegistration >();
		
		for ( final ViewId viewId : oldRegMap.keySet() )
			if ( views.contains( viewId ) )
				newRegMap.put( viewId, oldRegMap.get( viewId ) );

		final ViewRegistrations viewRegistrations = new ViewRegistrations( newRegMap );
		
		// re-assemble the interestpoints and a list of filenames to copy
		final Map< ViewId, ViewInterestPointLists > oldInterestPoints = lpq.getData().getViewInterestPoints().getViewInterestPoints();
		final Map< ViewId, ViewInterestPointLists > newInterestPoints = new HashMap< ViewId, ViewInterestPointLists >();

		for ( final ViewId viewId : oldInterestPoints.keySet() )
			if ( views.contains( viewId ) )
			{
				final ViewInterestPointLists ipLists = oldInterestPoints.get( viewId );
				newInterestPoints.put( viewId, ipLists );

				if ( filesToCopy != null )
				{
					// get also all the filenames that we need to copy
					for ( final InterestPointList ipl : ipLists.getHashMap().values() )
						filesToCopy.add( ipl.getFile().getName() );
				}
			}
		
		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( newInterestPoints );

		final SpimData2 newSpimData = new SpimData2(
				basePath,
				sequenceDescription,
				viewRegistrations,
				viewsInterestPoints );

		return newSpimData;
	}

	public static List< String > writeXML(
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

		filename += ".tif";

		if ( params.compress )
			filename += ".zip";

		// Re-assemble a new SpimData object containing the subset of viewsetups and timepoints selected
		final List< String > filesToCopy = new ArrayList< String >();
		final SpimData2 newSpimData = assemblePartialSpimData2( lpq, new File( params.xmlFile ).getParentFile(), filesToCopy );

		final StackImgLoaderIJ imgLoader = new StackImgLoaderIJ(
				new File( params.xmlFile ).getParentFile(),
				filename, params.imgFactory,
				layoutTP, layoutChannels, layoutIllum, layoutAngles, null );
		newSpimData.getSequenceDescription().setImgLoader( imgLoader );

		lpq.getIO().save( newSpimData, new File( params.xmlFile ).getAbsolutePath() );
		
		progressWriter.setProgress( 0.95 );

		return filesToCopy;
	}
	
	protected static String listAllTimePoints( final List<TimePoint> timePointsToProcess )
	{
		String t = "" + timePointsToProcess.get( 0 ).getId();

		for ( int i = 1; i < timePointsToProcess.size(); ++i )
			t += ", " + timePointsToProcess.get( i ).getId();

		return t;
	}

	public static void copyFolder( final File src, final File dest, final List< String > filesToCopy ) throws IOException
	{
		if ( src.isDirectory() )
		{
			if( !dest.exists() )
				dest.mkdir();

			for ( final String file : src.list() )
				copyFolder( new File( src, file ), new File( dest, file ), filesToCopy );
		}
		else
		{
			boolean contains = false;
			
			for ( int i = 0; i < filesToCopy.size() && !contains; ++i )
				if ( src.getName().contains( filesToCopy.get( i ) ) )
					contains = true;
			
			if ( contains )
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
}
