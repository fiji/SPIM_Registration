package spim.fiji.plugin;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.thinout.ChannelProcessThinOut;
import spim.fiji.spimdata.SpimData2;
import spim.process.removeDetection.DetectionRemoval;

import java.util.ArrayList;
import java.util.List;

public class ThinOut_Detections implements PlugIn
{
	public static String[] defaultNewLabels;
	public static int[] defaultRemoveKeep;
	public static double[] defaultCutoffThresholdMin, defaultCutoffThresholdMax;

	public static String[] removeKeepChoice = new String[]{ "Remove Range", "Keep Range" };
	public static double defaultThresholdMinValue = 0;
	public static double defaultThresholdMaxValue = 5;
	public static String defaultNewLabelText = "thinned-out";
	public static int defaultRemoveKeepValue = 0; // 0 == remove, 1 == keep

	public void run( final String arg )
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "", true, false, true, true ) )
			return;

		final SpimData2 data = xml.getData();
		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess() );

		// ask which channels have the objects we are searching for
		final List< ChannelProcessThinOut > channels = getChannelsAndLabels( data, viewIds );

		if ( channels == null )
			return;

		// get the actual min/max thresholds for cutting out
		if ( !getThinOutThresholds( data, viewIds, channels ) )
			return;

		// thin out detections and save the new interestpoint files
		if ( !DetectionRemoval.thinOut(data, viewIds, channels, true) )
			return;

		// write new xml
		SpimData2.saveXML( data, xml.getXMLFileName(), xml.getClusterExtension() );
	}

    public static boolean getThinOutThresholds( final SpimData2 spimData, final List< ViewId > viewIds, final List< ChannelProcessThinOut > channels )
	{

		if ( defaultCutoffThresholdMin == null || defaultCutoffThresholdMin.length != channels.size() || 
				defaultCutoffThresholdMax == null || defaultCutoffThresholdMax.length != channels.size() )
		{
			defaultCutoffThresholdMin = new double[ channels.size() ];
			defaultCutoffThresholdMax = new double[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
			{
				defaultCutoffThresholdMin[ i ] = defaultThresholdMinValue;
				defaultCutoffThresholdMax[ i ] = defaultThresholdMaxValue;
			}
		}

		if ( defaultRemoveKeep == null || defaultRemoveKeep.length != channels.size() )
		{
			defaultRemoveKeep = new int[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				defaultRemoveKeep[ i ] = defaultRemoveKeepValue;
		}

		final GenericDialog gd = new GenericDialog( "Define cut-off threshold" );

		for ( int c = 0; c < channels.size(); ++c )
		{
			final ChannelProcessThinOut channel = channels.get( c );
			gd.addChoice( "Channel_" + channel.getChannel().getName() + "_", removeKeepChoice, removeKeepChoice[ defaultRemoveKeep[ c ] ] );
			gd.addNumericField( "Channel_" + channel.getChannel().getName() + "_range_lower_threshold", defaultCutoffThresholdMin[ c ], 2 );
			gd.addNumericField( "Channel_" + channel.getChannel().getName() + "_range_upper_threshold", defaultCutoffThresholdMax[ c ], 2 );
			gd.addMessage( "" );
		}

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		for ( int c = 0; c < channels.size(); ++c )
		{
			final ChannelProcessThinOut channel = channels.get( c );

			final int removeKeep = defaultRemoveKeep[ c ] = gd.getNextChoiceIndex();
			if ( removeKeep == 1 )
				channel.setKeepRange( true );
			else
				channel.setKeepRange( false );
			channel.setMin( defaultCutoffThresholdMin[ c ] = gd.getNextNumber() );
			channel.setMax( defaultCutoffThresholdMax[ c ] = gd.getNextNumber() );

			if ( channel.getMin() >= channel.getMax() )
			{
				IOFunctions.println( "You selected the minimal threshold larger than the maximal threshold for channel " + channel.getChannel().getName() );
				IOFunctions.println( "Stopping." );
				return false;
			}
			else
			{
				if ( channel.keepRange() )
					IOFunctions.println( "Channel " + channel.getChannel().getName() + ": keep only distances from " + channel.getMin() + " >>> " + channel.getMax() );
				else
					IOFunctions.println( "Channel " + channel.getChannel().getName() + ": remove distances from " + channel.getMin() + " >>> " + channel.getMax() );
			}
		}

		return true;
	}

    public static ArrayList< ChannelProcessThinOut > getChannelsAndLabels(
			final SpimData2 spimData,
			final List< ViewId > viewIds )
	{
		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose segmentations to thin out" );

		final List< Channel > channels = SpimData2.getAllChannelsSorted( spimData, viewIds );
		final int nAllChannels = spimData.getSequenceDescription().getAllChannelsOrdered().size();

		if ( Interest_Point_Registration.defaultChannelLabels == null || Interest_Point_Registration.defaultChannelLabels.length != nAllChannels )
			Interest_Point_Registration.defaultChannelLabels = new int[ nAllChannels ];

		if ( defaultNewLabels == null || defaultNewLabels.length != channels.size() )
		{
			defaultNewLabels = new String[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				defaultNewLabels[ i ] = defaultNewLabelText;
		}

		// check which channels and labels are available and build the choices
		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int j = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel(
					spimData,
					viewIds,
					channel,
					"thin out" );

			if ( Interest_Point_Registration.defaultChannelLabels[ j ] >= labels.length )
				Interest_Point_Registration.defaultChannelLabels[ j ] = 0;

			String ch = channel.getName().replace( ' ', '_' );
			gd.addChoice("Channel_" + ch + "_Interest_points", labels, labels[Interest_Point_Registration.defaultChannelLabels[j]]);
			gd.addStringField("Channel_" + ch + "_New_label", defaultNewLabels[j], 20);

			channelLabels.add( labels );
			++j;
		}

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		// assemble which channels have been selected with with label
		final ArrayList< ChannelProcessThinOut > channelsToProcess = new ArrayList< ChannelProcessThinOut >();
		j = 0;
		
		for ( final Channel channel : channels )
		{
			final int channelChoice = Interest_Point_Registration.defaultChannelLabels[ j ] = gd.getNextChoiceIndex();
			final String newLabel = defaultNewLabels[ j ] = gd.getNextString();

			if ( channelChoice < channelLabels.get( j ).length - 1 )
			{
				String label = channelLabels.get( j )[ channelChoice ];

				if ( label.contains( Interest_Point_Registration.warningLabel ) )
					label = label.substring( 0, label.indexOf( Interest_Point_Registration.warningLabel ) );

				channelsToProcess.add( new ChannelProcessThinOut( channel, label, newLabel, false, 1 ) );
			}

			++j;
		}

		return channelsToProcess;
	}

	public static void main( final String[] args )
	{
        GenericLoadParseQueryXML.defaultXMLfilename = "/Volumes/LaCie/150424_OP227_TubeScaleA2/Worm1_G1/dataset_detect.xml";
        new ThinOut_Detections().run(null);
	}
}
