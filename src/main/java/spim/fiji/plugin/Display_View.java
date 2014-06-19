package spim.fiji.plugin;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.export.ImgExport;

public class Display_View implements PlugIn
{
	public static int defaultAngleChoice = 0;
	public static int defaultChannelChoice = 0;
	public static int defaultIlluminationChoice = 0;
	public static int defaultTimepointChoice = 0;
	
	public static int defaultPixelType = 0;
	public static boolean defaultVirtual = true;
	
	@Override
	public void run(String arg0)
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "displaying a view", false, false, false, false ) )
			return;
		
		final GenericDialog gd = new GenericDialog( "Select View" );
		
		final List< TimePoint > timepoints = result.getTimePointsToProcess();
		final String[] timepointNames = new String[ timepoints.size() ];
		for ( int i = 0; i < timepointNames.length; ++i )
			timepointNames[ i ] = result.getTimePointsToProcess().get( i ).getName();
		
		final List< Angle > angles = result.getData().getSequenceDescription().getAllAnglesOrdered();
		final String[] angleNames = new String[ angles.size() ];
		for ( int i = 0; i < angles.size(); ++i )
			angleNames[ i ] = angles.get( i ).getName();

		final List< Channel > channels = result.getData().getSequenceDescription().getAllChannelsOrdered();
		final String[] channelNames = new String[ channels.size() ];
		for ( int i = 0; i < channels.size(); ++i )
			channelNames[ i ] = channels.get( i ).getName();

		final List< Illumination > illuminations = result.getData().getSequenceDescription().getAllIlluminationsOrdered();
		final String[] illuminationNames = new String[ illuminations.size() ];
		for ( int i = 0; i < illuminations.size(); ++i )
			illuminationNames[ i ] = illuminations.get( i ).getName();
		
		gd.addChoice( "Angle", angleNames, angleNames[ defaultAngleChoice ] );
		gd.addChoice( "Channel", channelNames, channelNames[ defaultChannelChoice ] );
		gd.addChoice( "Illumination", illuminationNames, illuminationNames[ defaultIlluminationChoice ] );
		gd.addChoice( "Timepoint", timepointNames, timepointNames[ defaultTimepointChoice ] );
		gd.addMessage( "" );
		gd.addChoice( "Pixel_type", BoundingBox.pixelTypes, BoundingBox.pixelTypes[ defaultPixelType ] );
		gd.addCheckbox( "Virtual_displaying (otherwise copy to ImageJ image)", defaultVirtual );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		final Angle angle = angles.get( defaultAngleChoice = gd.getNextChoiceIndex() );
		final Channel channel = channels.get( defaultAngleChoice = gd.getNextChoiceIndex() );
		final Illumination illumination = illuminations.get( defaultIlluminationChoice = gd.getNextChoiceIndex() );
		final TimePoint tp = timepoints.get( defaultTimepointChoice = gd.getNextChoiceIndex() );
		final int pixelType = defaultPixelType = gd.getNextChoiceIndex();
		final boolean virtual = defaultVirtual = gd.getNextBoolean();
		
		// get the corresponding viewid
		final ViewId viewId = SpimData2.getViewId( result.getData().getSequenceDescription(), tp, channel, angle, illumination );
		final String name = "angle: " + angle.getName() + " channel: " + channel.getName() + " illum: " + illumination.getName() + " timepoint: " + tp.getName();
		
		if ( viewId == null )
		{
			IOFunctions.println( "An error occured. Count not find the corresponding ViewSetup for " + name );
			
			return;
		}
		
		// get the viewdescription
		final ViewDescription viewDescription = result.getData().getSequenceDescription().getViewDescription( 
				viewId.getTimePointId(), viewId.getViewSetupId() );

		// check if this viewid is present in the current timepoint
		if ( !viewDescription.isPresent() )
			IOFunctions.println( "This ViewSetup is not present for this timepoint: angle: " + name );
		
		// display it
		ImgExport export = new DisplayImage( virtual );
		
		if ( pixelType == 0 )
			export.exportImage( result.getData().getSequenceDescription().getImgLoader().getFloatImage( viewId, false ), null, name );
		else
		{
			@SuppressWarnings( "unchecked" )
			RandomAccessibleInterval< UnsignedShortType > img = ( RandomAccessibleInterval< UnsignedShortType > ) result.getData().getSequenceDescription().getImgLoader().getImage( viewId );
			export.exportImage( img, null, name );
		}
	}

	public static void main( String[] args )
	{
		new Display_View().run( null );
	}
}
