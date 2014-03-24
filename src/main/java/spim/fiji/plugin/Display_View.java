package spim.fiji.plugin;

import java.util.ArrayList;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.spimdata.SpimData2;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class Display_View implements PlugIn
{
	public static int defaultAngleChoice = 0;
	public static int defaultChannelChoice = 0;
	public static int defaultIlluminationChoice = 0;
	public static int defaultTimepointChoice = 0;
	
	@Override
	public void run(String arg0)
	{
		// ask for everything but the channels
		final XMLParseResult result = new LoadParseQueryXML().queryXML( false, false, false, false );
		
		if ( result == null )
			return;
		
		final GenericDialog gd = new GenericDialog( "Select View" );
		
		final ArrayList< TimePoint > timepoints = result.getTimePointsToProcess();
		final String[] timepointNames = new String[ timepoints.size() ];
		for ( int i = 0; i < timepointNames.length; ++i )
			timepointNames[ i ] = result.getTimePointsToProcess().get( i ).getName();
		
		final ArrayList< Angle > angles = result.getData().getSequenceDescription().getAllAngles();
		final String[] angleNames = new String[ angles.size() ];
		for ( int i = 0; i < angles.size(); ++i )
			angleNames[ i ] = angles.get( i ).getName();

		final ArrayList< Channel > channels = result.getData().getSequenceDescription().getAllChannels();
		final String[] channelNames = new String[ channels.size() ];
		for ( int i = 0; i < channels.size(); ++i )
			channelNames[ i ] = channels.get( i ).getName();

		final ArrayList< Illumination > illuminations = result.getData().getSequenceDescription().getAllIlluminations();
		final String[] illuminationNames = new String[ illuminations.size() ];
		for ( int i = 0; i < illuminations.size(); ++i )
			illuminationNames[ i ] = illuminations.get( i ).getName();
		
		gd.addChoice( "Angle", angleNames, angleNames[ defaultAngleChoice ] );
		gd.addChoice( "Channel", channelNames, channelNames[ defaultChannelChoice ] );
		gd.addChoice( "Illumination", illuminationNames, illuminationNames[ defaultIlluminationChoice ] );
		gd.addChoice( "Timepoint", timepointNames, timepointNames[ defaultTimepointChoice ] );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		final Angle angle = angles.get( defaultAngleChoice = gd.getNextChoiceIndex() );
		final Channel channel = channels.get( defaultAngleChoice = gd.getNextChoiceIndex() );
		final Illumination illumination = illuminations.get( defaultIlluminationChoice = gd.getNextChoiceIndex() );
		final TimePoint tp = timepoints.get( defaultTimepointChoice = gd.getNextChoiceIndex() );
		
		// get the corresponding viewid
		final ViewId viewId = SpimData2.getViewId( result.getData().getSequenceDescription(), tp, channel, angle, illumination );

		if ( viewId == null )
		{
			IOFunctions.println( "An error occured. Count not find the corresponding ViewSetup for angle: " + angle.getName() + 
					" channel: " + channel.getName() + " illum: " + illumination.getName() + " timepoint: " + tp.getName() );
			
			return;
		}
		
		// get the viewdescription
		final ViewDescription< TimePoint, ViewSetup > viewDescription = result.getData().getSequenceDescription().getViewDescription( 
				viewId.getTimePointId(), viewId.getViewSetupId() );

		// check if this viewid is present in the current timepoint
		if ( !viewDescription.isPresent() )
			IOFunctions.println( "This ViewSetup is not present for this timepoint: angle: " + angle.getId() + 
					" channel: " + channel.getId() + " illum: " + illumination.getId() + " timepoint: " + tp.getName() );
		
		// display it
		final RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > img = 
				result.getData().getSequenceDescription().getImgLoader().getImage( viewDescription, false );
		
		ImageJFunctions.show( img );
	}

	public static void main( String[] args )
	{
		new Display_View().run( null );
	}
}
