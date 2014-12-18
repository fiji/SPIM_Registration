package spim.fiji.plugin.resave;

import ij.plugin.PlugIn;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
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
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.resave.Generic_Resave_HDF5.Parameters;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.img.hdf5.Hdf5ImageLoader;

public class Resave_HDF5 implements PlugIn
{
	public static void main( final String[] args )
	{
		new Resave_HDF5().run( null );
	}

	@Override
	public void run( final String arg0 )
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();
		
		if ( !xml.queryXML( "Resaving as HDF5", "Resave", true, true, true, true ) )
			return;

		// load all dimensions if they are not known (required for estimating the mipmap layout)
		if ( loadDimensions( xml.getData(), xml.getViewSetupsToProcess() ) )
		{
			// save the XML again with the dimensions loaded
			final XmlIoSpimData2 io = new XmlIoSpimData2();
			
			final String xmlFile = new File( xml.getData().getBasePath(), new File( xml.getXMLFileName() ).getName() ).getAbsolutePath();
			try 
			{
				io.save( xml.getData(), xmlFile );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + xmlFile + "'." );
			}
			catch ( Exception e )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + xmlFile + "': " + e );
				e.printStackTrace();
			}
		}

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = proposeMipmaps( xml.getViewSetupsToProcess() );

		Generic_Resave_HDF5.lastExportPath = LoadParseQueryXML.defaultXMLfilename;

		final int firstviewSetupId = xml.getData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
		final Parameters params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true );

		if ( params == null )
			return;

		LoadParseQueryXML.defaultXMLfilename = params.getSeqFile().toString();

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		// write hdf5
		Generic_Resave_HDF5.writeHDF5( reduceSpimData2( xml.getData(), xml.getTimePointsToProcess(), xml.getViewSetupsToProcess() ).getSequenceDescription(), params, perSetupExportMipmapInfo, progressWriter );

		// write xml sequence description
		try
		{
			final List< String > filesToCopy = writeXML( xml, params.getSeqFile(), params.getHDF5File(), progressWriter );

			// copy the interest points if they exist
			Resave_TIFF.copyInterestPoints( xml.getData().getBasePath(), params.getSeqFile().getParentFile(), filesToCopy );
		} 
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + params.getSeqFile() + "': " + e );
			throw new RuntimeException( e );
		}
		finally
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + params.getSeqFile() + "'." );
		}
	}

	public static Map< Integer, ExportMipmapInfo > proposeMipmaps( final List< ? extends BasicViewSetup > viewsetups )
	{
		final HashMap< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = new HashMap< Integer, ExportMipmapInfo >();
		for ( final BasicViewSetup setup : viewsetups )
			perSetupExportMipmapInfo.put( setup.getId(), ProposeMipmaps.proposeMipmaps( setup ) );
		return perSetupExportMipmapInfo;
	}


	public static boolean loadDimensions( final SpimData2 spimData, final List< ViewSetup > viewsetups )
	{
		boolean loadedDimensions = false;
		
		for ( final ViewSetup vs : viewsetups )
		{
			if ( vs.getSize() == null )
			{
				IOFunctions.println( "Dimensions of viewsetup " + vs.getId() + " unknown. Loading them ... " );
				
				for ( final TimePoint t : spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
				{
					final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( t.getId(), vs.getId() );
					
					if ( vd.isPresent() )
					{
						vs.setSize( spimData.getSequenceDescription().getImgLoader().getImageSize( vd ) );
						loadedDimensions = true;
						break;
					}
				}
			}
		}
		
		return loadedDimensions;
	}
	
	/**
	 * Reduces a given SpimData2 to the subset of timepoints and viewsetups as selected by the user, including the original imgloader.
	 * 
	 * @param oldSpimData
	 * @param timepointsToProcess
	 * @param viewSetupsToProcess
	 * @return
	 */
	public static SpimData2 reduceSpimData2( final SpimData2 oldSpimData, final List< TimePoint > timepointsToProcess, final List< ViewSetup > viewSetupsToProcess )
	{
		final TimePoints timepoints;

		try
		{
			timepoints = new TimePointsPattern( Resave_TIFF.listAllTimePoints( timepointsToProcess ) );
		}
		catch (ParseException e)
		{
			IOFunctions.println( "Automatically created list of timepoints failed to parse. This should not happen, really :) -- " + e );
			IOFunctions.println( "Here is the list: " + Resave_TIFF.listAllTimePoints( timepointsToProcess ) );
			e.printStackTrace();
			return null;
		}

		// a hashset for all viewsetups that remain
		final Set< ViewId > views = new HashSet< ViewId >();
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final ViewSetup v : viewSetupsToProcess )
				views.add( new ViewId( t.getId(), v.getId() ) );

		final MissingViews oldMissingViews = oldSpimData.getSequenceDescription().getMissingViews();
		final ArrayList< ViewId > missingViews = new ArrayList< ViewId >();

		if( oldMissingViews != null && oldMissingViews.getMissingViews() != null )
			for ( final ViewId id : oldMissingViews.getMissingViews() )
				if ( views.contains( id ) )
					missingViews.add( id );

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, viewSetupsToProcess, oldSpimData.getSequenceDescription().getImgLoader(), new MissingViews( missingViews ) );

		// re-assemble the registrations
		final Map< ViewId, ViewRegistration > oldRegMap = oldSpimData.getViewRegistrations().getViewRegistrations();
		final Map< ViewId, ViewRegistration > newRegMap = new HashMap< ViewId, ViewRegistration >();
		
		for ( final ViewId viewId : oldRegMap.keySet() )
			if ( views.contains( viewId ) )
				newRegMap.put( viewId, oldRegMap.get( viewId ) );

		final ViewRegistrations viewRegistrations = new ViewRegistrations( newRegMap );
		
		// re-assemble the interestpoints and a list of filenames to copy
		final Map< ViewId, ViewInterestPointLists > oldInterestPoints = oldSpimData.getViewInterestPoints().getViewInterestPoints();
		final Map< ViewId, ViewInterestPointLists > newInterestPoints = new HashMap< ViewId, ViewInterestPointLists >();

		for ( final ViewId viewId : oldInterestPoints.keySet() )
			if ( views.contains( viewId ) )
				newInterestPoints.put( viewId, oldInterestPoints.get( viewId ) );
		
		final ViewInterestPoints viewsInterestPoints = new ViewInterestPoints( newInterestPoints );
		
		final SpimData2 newSpimData = new SpimData2(
				oldSpimData.getBasePath(),
				sequenceDescription,
				viewRegistrations,
				viewsInterestPoints );

		return newSpimData;
	}

	public static List< String > writeXML(
			final LoadParseQueryXML lpq,
			final File seqFile,
			final File hdf5File,
			final ProgressWriter progressWriter )
		throws SpimDataException
	{
		// Re-assemble a new SpimData object containing the subset of viewsetups and timepoints selected
		final List< String > filesToCopy = new ArrayList< String >();
		final SpimData2 newSpimData = Resave_TIFF.assemblePartialSpimData2( lpq, seqFile.getParentFile(), filesToCopy );
				
		final Hdf5ImageLoader hdf5Loader = new Hdf5ImageLoader( hdf5File, null, null, false );
		newSpimData.getSequenceDescription().setImgLoader( hdf5Loader );
		newSpimData.setBasePath( seqFile.getParentFile() );

		lpq.getIO().save( newSpimData, seqFile.getAbsolutePath() );
		
		progressWriter.setProgress( 0.95 );
		
		return filesToCopy;
	}
}
