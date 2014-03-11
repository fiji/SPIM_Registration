package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.plugin.interestpointdetection.DifferenceOf;
import spim.fiji.plugin.interestpointdetection.DifferenceOfGaussian;
import spim.fiji.plugin.interestpointdetection.DifferenceOfMean;
import spim.fiji.plugin.interestpointdetection.InterestPointDetection;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIo;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;

/**
 * Plugin to detect interest points, store them on disk, and link them into the XML
 * 
 * Different plugins to detect interest points are supported, needs to implement the
 * {@link InterestPointDetection} interface
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Interest_Point_Detection implements PlugIn
{
	public static ArrayList< InterestPointDetection > staticAlgorithms = new ArrayList< InterestPointDetection >();
	public static int defaultAlgorithm = 0;
	public static boolean[] defaultChannelChoice = null;
	public static String defaultLabel = "beads";
	
	static
	{
		IOFunctions.printIJLog = true;
		staticAlgorithms.add( new DifferenceOfMean( null, null, null, null, null ) );
		staticAlgorithms.add( new DifferenceOfGaussian( null, null, null, null, null ) );
	}
	
	@Override
	public void run( final String arg )
	{
		// ask for everything but the channels
		final XMLParseResult result = new LoadParseQueryXML().queryXML( true, false, true, true );
		
		if ( result == null )
			return;
		
		// ask which channels have the objects we are searching for
		final ArrayList< Channel > channels = result.getChannelsToProcess(); //result.getData().getSequenceDescription().getAllChannels();
		
		// the GenericDialog needs a list[] of String
		final String[] descriptions = new String[ staticAlgorithms.size() ];
		
		for ( int i = 0; i < staticAlgorithms.size(); ++i )
			descriptions[ i ] = staticAlgorithms.get( i ).getDescription();
		
		if ( defaultAlgorithm >= descriptions.length )
			defaultAlgorithm = 0;
		
		final GenericDialog gd = new GenericDialog( "Detect Interest Points" );
		
		gd.addChoice( "Type_of_interest_point_detection", descriptions, descriptions[ defaultAlgorithm ] );
		gd.addStringField( "Label_interest_points", defaultLabel );
		
		if ( channels.size() > 1 )
		{
			if ( defaultChannelChoice == null || defaultChannelChoice.length != channels.size() )
			{
				defaultChannelChoice = new boolean[ channels.size() ];
				for ( int i = 0; i < channels.size(); ++i )
					defaultChannelChoice[ i ] = true;
			}
			
			gd.addMessage( "" );
			gd.addMessage( "Choose channels to detect interest points in", GUIHelper.largefont );
			
			for ( int i = 0; i < channels.size(); ++i )
				gd.addCheckbox( "Channel_" + channels.get( i ).getName(), defaultChannelChoice[ i ] );
		}
		
		gd.addMessage( "" );
		GUIHelper.addWebsite( gd );
		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		final int algorithm = defaultAlgorithm = gd.getNextChoiceIndex();

		// how are the detections called (e.g. beads, nuclei, ...)
		final String label = defaultLabel = gd.getNextString();
		final ArrayList< Channel> channelsToProcess = new ArrayList< Channel >();
		
		if ( channels.size() > 1 )
		{
			for ( int i = 0; i < channels.size(); ++i )
				if ( defaultChannelChoice[ i ] = gd.getNextBoolean() )
					channelsToProcess.add( channels.get( i ) );
			
			if ( channelsToProcess.size() == 0 )
			{
				IOFunctions.println( "No channels selected. Quitting." );
				return;
			}
		}
		else
		{
			channelsToProcess.add( channels.get( 0 ) );
		}

		final InterestPointDetection ipd = staticAlgorithms.get( algorithm ).newInstance(
				result.getData(),
				result.getAnglesToProcess(),
				channelsToProcess,
				result.getIlluminationsToProcess(),
				result.getTimePointsToProcess() );
		
		// the interest point detection should query its parameters
		ipd.queryParameters();
		
		// now extract all the detections
		final HashMap< ViewId, List< InterestPoint > > points = ipd.findInterestPoints();
		
		if ( ipd instanceof DifferenceOf )
		{
			IOFunctions.println( "Opening of files took: " + ((DifferenceOf)ipd).getBenchmark().openFiles/1000 + " sec." );
			IOFunctions.println( "Detecting interest points took: " + ((DifferenceOf)ipd).getBenchmark().computation/1000 + " sec." );
		}
		
		// save the file and the path in the XML
		final SpimData2 data = result.getData();
		final SequenceDescription< TimePoint, ViewSetup > seqDesc = data.getSequenceDescription();
		
		for ( final ViewId viewId : points.keySet() )
		{
			final ViewDescription< TimePoint, ViewSetup > viewDesc = seqDesc.getViewDescription( viewId.getTimePointId(), viewId.getViewSetupId() );
			final int channelId = viewDesc.getViewSetup().getChannel().getId();		
			
			final InterestPointList list = new InterestPointList(
					data.getBasePath(),
					new File( "interestpoints", "tpId_" + viewId.getTimePointId() + "_viewSetupId_" + viewId.getViewSetupId() + "." + label ) );
			
			list.setParameters( ipd.getParameters( channelId ) );
			list.setInterestPoints( points.get( viewId ) );
			
			if ( !list.saveInterestPoints() )
			{
				IOFunctions.println( "Error saving interest point list: " + new File( list.getBaseDir(), list.getFile().toString() + list.getInterestPointsExt() ) );
				return;
			}
			
			final ViewInterestPointLists vipl = data.getViewInterestPoints().getViewInterestPointLists( viewId );
			vipl.addInterestPointList( label, list );
		}
		
		// save the xml
		final XmlIoSpimData2 io = XmlIo.createDefaultIo();
		
		final String xml = new File( data.getBasePath(), new File( result.getXMLFileName() ).getName() ).getAbsolutePath();
		try 
		{
			io.save( data, xml );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + xml + "'." );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + xml + "': " + e );
			e.printStackTrace();
		}
	}
	
	public static void main( final String[] args )
	{
		new ImageJ();
		new Interest_Point_Detection().run( null );
	}
}
