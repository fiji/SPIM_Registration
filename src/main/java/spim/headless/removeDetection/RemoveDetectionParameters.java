package spim.headless.removeDetection;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.plugin.thinout.ChannelProcessThinOut;
import spim.fiji.spimdata.SpimData2;
import spim.headless.interestpointdetection.DoG;
import spim.headless.interestpointdetection.DoGParameters;
import spim.process.removeDetection.DetectionRemoval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Stephan Janosch on 01/07/15.
 */
public class RemoveDetectionParameters {

    protected Collection< ViewDescription > toProcess;
    protected List<ChannelProcessThinOut> channelsToProcess;
    protected String newLabel;
    protected String label;
    protected double min, max;
    protected boolean keepRange;



    public static void main(String[] args)
    {

        SpimData spimData = SimulatedBeadsImgLoader.spimdataExample();
        ImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();
        List<ViewDescription> viewDescriptions = new ArrayList<ViewDescription>();
        viewDescriptions.addAll(spimData.getSequenceDescription().getViewDescriptions().values());

        DoGParameters dog = new DoGParameters(viewDescriptions,imgLoader,1.4,2);


          DoG.findInterestPoints(dog);


        RemoveDetectionParameters removeDetectionParameters = new RemoveDetectionParameters();

        removeDetectionParameters.toProcess = new ArrayList<ViewDescription>();
//        removeDetectionParameters.toProcess.addAll(spimData.getViewRegistrations().getViewRegistrations().values());
//
//        final ArrayList< ChannelProcessThinOut > channelsToProcess = new ArrayList< ChannelProcessThinOut >();
//        Channel channel = spimData.getSequenceDescription().getAllChannelsOrdered().get(0);
//
//        channelsToProcess.add(
//                new ChannelProcessThinOut( channel, "beads", "thinned_beads", false, 0)
//        );
//        removeDetectionParameters.channelsToProcess = channelsToProcess;

//        DetectionRemoval.thinOut(spimData, viewIds, removeDetectionParameters.channelsToProcess, true);

    }


}
