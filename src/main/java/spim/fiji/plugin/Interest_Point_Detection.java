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
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.interestpointdetection.DifferenceOf;
import spim.fiji.plugin.interestpointdetection.DifferenceOfGaussian;
import spim.fiji.plugin.interestpointdetection.DifferenceOfMean;
import spim.fiji.plugin.interestpointdetection.InterestPointDetection;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
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
	public static int defaultAlgorithm = 1;
	public static boolean defaultDownSample = true;
	public static boolean defaultDefineAnisotropy = false;
	public static boolean defaultAdditionalSmoothing = false;
	public static boolean defaultSetMinMax = false;
	public static boolean defaultLimitDetections = false;
	public static String defaultLabel = "beads";
	
	static
	{
		IOFunctions.printIJLog = true;
		staticAlgorithms.add( new DifferenceOfMean( null, null ) );
		staticAlgorithms.add( new DifferenceOfGaussian( null, null ) );
	}
	
	@Override
	public void run( final String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "perfoming interest point detection", true, true, true, true ) )
			return;

		detectInterestPoints(
				result.getData(),
				SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ),
				result.getClusterExtension(),
				result.getXMLFileName(),
				true );
	}

	/**
	 * Does just the detection, no saving
	 * 
	 * @param data
	 * @param viewIds
	 * @return
	 */
	public boolean detectInterestPoints(
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		return detectInterestPoints( data, viewIds, "", null, false );
	}

	public boolean detectInterestPoints(
			final SpimData2 data,
			final List< ViewId > viewIds,
			final String xmlFileName,
			final boolean saveXML )
	{
		return detectInterestPoints( data, viewIds, "", xmlFileName, saveXML );
	}

	public boolean detectInterestPoints(
			final SpimData2 data,
			final List< ViewId > viewIds,
			final String clusterExtension,
			final String xmlFileName,
			final boolean saveXML )
	{
		// the GenericDialog needs a list[] of String
		final String[] descriptions = new String[ staticAlgorithms.size() ];
		
		for ( int i = 0; i < staticAlgorithms.size(); ++i )
			descriptions[ i ] = staticAlgorithms.get( i ).getDescription();
		
		if ( defaultAlgorithm >= descriptions.length )
			defaultAlgorithm = 0;
		
		final GenericDialog gd = new GenericDialog( "Detect Interest Points" );
		
		gd.addChoice( "Type_of_interest_point_detection", descriptions, descriptions[ defaultAlgorithm ] );
		gd.addStringField( "Label_interest_points", defaultLabel );

		gd.addMessage( "" );
		gd.addMessage( "Channels to detect interest points in", GUIHelper.largefont );

		final ArrayList< Channel > channels = SpimData2.getAllChannelsSorted( data, viewIds );

		for ( int i = 0; i < channels.size(); ++i )
			gd.addMessage( "Channel " + channels.get( i ).getName(), GUIHelper.smallStatusFont );

		gd.addMessage( "" );

		gd.addCheckbox( "Downsample_images prior to segmentation", defaultDownSample );
		gd.addCheckbox( "Define_anisotropy for segmentation", defaultDefineAnisotropy );
		gd.addCheckbox( "Additional_smoothing", defaultAdditionalSmoothing );
		gd.addCheckbox( "Set_minimal_and_maximal_intensity", defaultSetMinMax );
		gd.addCheckbox( "Limit_amount_of_detections" , defaultLimitDetections );
		
		gd.addMessage( "" );
		GUIHelper.addWebsite( gd );
		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		final int algorithm = defaultAlgorithm = gd.getNextChoiceIndex();

		// how are the detections called (e.g. beads, nuclei, ...)
		final String label = defaultLabel = gd.getNextString();
		final ArrayList< Channel> channelsToProcess = new ArrayList< Channel >();

		for ( int i = 0; i < channels.size(); ++i )
			channelsToProcess.add( channels.get( i ) );

		final boolean downsample = defaultDownSample = gd.getNextBoolean();
		final boolean defineAnisotropy = defaultDefineAnisotropy = gd.getNextBoolean();
		final boolean additionalSmoothing = defaultAdditionalSmoothing = gd.getNextBoolean();
		final boolean setMinMax = defaultSetMinMax = gd.getNextBoolean();
		final boolean limitDetections = defaultLimitDetections = gd.getNextBoolean();
		
		final InterestPointDetection ipd = staticAlgorithms.get( algorithm ).newInstance(
				data,
				viewIds );
		
		// the interest point detection should query its parameters
		if ( !ipd.queryParameters( downsample, defineAnisotropy, additionalSmoothing, setMinMax, limitDetections ) )
			return false;
		
		// now extract all the detections
		for ( final TimePoint tp : SpimData2.getAllTimePointsSorted( data, viewIds ) )
		{
			final HashMap< ViewId, List< InterestPoint > > points = ipd.findInterestPoints( tp );
			
			if ( ipd instanceof DifferenceOf )
			{
				IOFunctions.println( "Opening of files took: " + ((DifferenceOf)ipd).getBenchmark().openFiles/1000 + " sec." );
				IOFunctions.println( "Detecting interest points took: " + ((DifferenceOf)ipd).getBenchmark().computation/1000 + " sec." );
			}
			
			// save the file and the path in the XML
			final SequenceDescription seqDesc = data.getSequenceDescription();
			
			for ( final ViewId viewId : points.keySet() )
			{
				final ViewDescription viewDesc = seqDesc.getViewDescription( viewId.getTimePointId(), viewId.getViewSetupId() );
				final int channelId = viewDesc.getViewSetup().getChannel().getId();		
				
				final InterestPointList list = new InterestPointList(
						data.getBasePath(),
						new File( "interestpoints", "tpId_" + viewId.getTimePointId() + "_viewSetupId_" + viewId.getViewSetupId() + "." + label ) );
				
				list.setParameters( ipd.getParameters( channelId ) );
				list.setInterestPoints( points.get( viewId ) );

				if ( saveXML )
				{
					if ( !list.saveInterestPoints() )
					{
						IOFunctions.println( "Error saving interest point list: " + new File( list.getBaseDir(), list.getFile().toString() + list.getInterestPointsExt() ) );
						return false;
					}
	
					list.setCorrespondingInterestPoints( new ArrayList< CorrespondingInterestPoints >() );
					if ( !list.saveCorrespondingInterestPoints() )
						IOFunctions.println( "Failed to clear corresponding interest point list: " + new File( list.getBaseDir(), list.getFile().toString() + list.getCorrespondencesExt() ) );
				}

				final ViewInterestPointLists vipl = data.getViewInterestPoints().getViewInterestPointLists( viewId );
				vipl.addInterestPointList( label, list );
			}
			
			// update metadata if necessary
			if ( data.getSequenceDescription().getImgLoader() instanceof AbstractImgLoader )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Updating metadata ... " );
				try
				{
					( (AbstractImgLoader)data.getSequenceDescription().getImgLoader() ).updateXMLMetaData( data, false );
				}
				catch( Exception e )
				{
					IOFunctions.println( "Failed to update metadata, this should not happen: " + e );
				}
			}
			
			// save the xml
			if ( saveXML )
				SpimData2.saveXML( data, xmlFileName, clusterExtension );
		}

		return true;
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new Interest_Point_Detection().run( null );
	}
}
