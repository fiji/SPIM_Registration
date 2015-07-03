package spim.fiji.plugin;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.thinout.ChannelProcessThinOut;
import spim.fiji.spimdata.SpimData2;
import spim.headless.interestpointdetection.DoG;
import spim.headless.interestpointdetection.DoGParameters;
import spim.headless.interestpointdetection.InterestPointTools;
import spim.process.histogram.HistogramDisplay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stephan Janosch on 01/07/15.
 */
public class Show_Histogram implements PlugIn {

    public static boolean[] defaultShowHistogram;
    public static int[] defaultSubSampling;
    public static int defaultSubSamplingValue = 1;


    public static ArrayList< ChannelProcessThinOut > getChannelsAndLabels(
            final SpimData2 spimData,
            final List< ViewId > viewIds )
    {
        // build up the dialog
        final GenericDialog gd = new GenericDialog( "Choose segmentations to show in histogram" );

        final List< Channel > channels = SpimData2.getAllChannelsSorted( spimData, viewIds );
        final int nAllChannels = spimData.getSequenceDescription().getAllChannelsOrdered().size();

        if ( Interest_Point_Registration.defaultChannelLabels == null || Interest_Point_Registration.defaultChannelLabels.length != nAllChannels )
            Interest_Point_Registration.defaultChannelLabels = new int[ nAllChannels ];

        if ( defaultShowHistogram == null || defaultShowHistogram.length != channels.size() )
        {
            defaultShowHistogram = new boolean[ channels.size() ];
            for ( int i = 0; i < channels.size(); ++i )
                defaultShowHistogram[ i ] = true;
        }

        if ( defaultSubSampling == null || defaultSubSampling.length != channels.size() )
        {
            defaultSubSampling = new int[ channels.size() ];
            for ( int i = 0; i < channels.size(); ++i )
                defaultSubSampling[ i ] = defaultSubSamplingValue;
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
            gd.addCheckbox( "Channel_" + ch + "_Display_distance_histogram", defaultShowHistogram[ j ] );
            gd.addChoice( "Channel_" + ch + "_Interest_points", labels, labels[ Interest_Point_Registration.defaultChannelLabels[ j ] ] );
            gd.addNumericField( "Channel_" + ch + "_Subsample histogram", defaultSubSampling[ j ], 0, 5, "times" );

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
            final boolean showHistogram = defaultShowHistogram[ j ] = gd.getNextBoolean();
            final int channelChoice = Interest_Point_Registration.defaultChannelLabels[ j ] = gd.getNextChoiceIndex();
            final int subSampling = defaultSubSampling[ j ] = (int)Math.round( gd.getNextNumber() );

            if ( channelChoice < channelLabels.get( j ).length - 1 )
            {
                String label = channelLabels.get( j )[ channelChoice ];

                if ( label.contains( Interest_Point_Registration.warningLabel ) )
                    label = label.substring( 0, label.indexOf( Interest_Point_Registration.warningLabel ) );

                channelsToProcess.add( new ChannelProcessThinOut( channel, label, "", showHistogram, subSampling ) );
            }

            ++j;
        }

        return channelsToProcess;
    }

    public void run(String arg) {
        final LoadParseQueryXML xml = new LoadParseQueryXML();

        if (!xml.queryXML("", true, false, true, true))
            return;

        final SpimData2 data = xml.getData();
        final List<ViewId> viewIds = SpimData2.getAllViewIdsSorted(data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess());
//        final List<Channel> channels= SpimData2.getAllChannelsSorted(data,viewIds);


//        // ask which channels have the objects we are searching for
        final List<ChannelProcessThinOut> channels = getChannelsAndLabels(data, viewIds);

        for ( final ChannelProcessThinOut channel : channels )
            if ( channel.showHistogram() )
                HistogramDisplay.plotHistogram(data, viewIds, channel);

    }

    private static void testShowHistogram()
    {
        SpimData spimData = SimulatedBeadsImgLoader.spimdataExample();
        SpimData2 spimData2 = SpimData2.convert(spimData);

        ImgLoader imgLoader = spimData2.getSequenceDescription().getImgLoader();
        List<ViewDescription> viewDescriptions = new ArrayList<ViewDescription>();
        viewDescriptions.addAll(spimData2.getSequenceDescription().getViewDescriptions().values());

        DoGParameters dog = new DoGParameters(viewDescriptions, imgLoader, 1.4, 2);

        String label = "ips";

        InterestPointTools.addInterestPoints(spimData2, label, DoG.findInterestPoints(dog), "");

        HistogramDisplay.plotHistogram(spimData2,viewDescriptions,label,1);

    }

    public static void main( final String[] args )
    {
//        GenericLoadParseQueryXML.defaultXMLfilename = "/Users/janosch/no_backup/one.xml";
//        new Show_Histogram().run( null );
        testShowHistogram();


    }
}
