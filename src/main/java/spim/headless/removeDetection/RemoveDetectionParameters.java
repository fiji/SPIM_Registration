package spim.headless.removeDetection;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.headless.interestpointdetection.DoG;
import spim.headless.interestpointdetection.DoGParameters;
import spim.headless.interestpointdetection.InterestPointTools;
import spim.process.removeDetection.DetectionRemoval;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Stephan Janosch on 01/07/15.
 */
public class RemoveDetectionParameters {

    /**
     * The view descriptions to work on
     */
    protected Collection< ViewDescription > toProcess;
    /**
     * The Label.
     */
    protected String label;
    /**
     * The Min.
     */
    protected double min;
    /**
     * The Max.
     */
    protected double max;
    /**
     * The Keep range.
     */
    protected boolean keepRange;


    /**
     * Gets to process.
     *
     * @return the to process
     */
    public Collection<ViewDescription> getToProcess() {
        return toProcess;
    }

    /**
     * Sets to process.
     *
     * @param toProcess the to process
     */
    public void setToProcess(Collection<ViewDescription> toProcess) {
        this.toProcess = toProcess;
    }

    /**
     * Gets label.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets label.
     *
     * @param label the label
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Gets min.
     *
     * @return the min
     */
    public double getMin() {
        return min;
    }

    /**
     * Sets min.
     *
     * @param min the min
     */
    public void setMin(double min) {
        this.min = min;
    }

    /**
     * Gets max.
     *
     * @return the max
     */
    public double getMax() {
        return max;
    }

    /**
     * Sets max.
     *
     * @param max the max
     */
    public void setMax(double max) {
        this.max = max;
    }

    /**
     * Gets keep range.
     *
     * @return the keep range
     */
    public boolean getKeepRange() {
        return keepRange;
    }

    /**
     * Sets keep range.
     *
     * @param keepRange the keep range
     */
    public void setKeepRange(boolean keepRange) {
        this.keepRange = keepRange;
    }


    /**
     * A method which tests the functionality
     *
     * @param args the input arguments
     */
    public static void main(String[] args)
    {

        SpimData spimData = SimulatedBeadsImgLoader.spimdataExample();
        SpimData2 spimData2 = SpimData2.convert(spimData);
        spimData2.setBasePath(new File("/Users/janosch/no_backup"));

        ImgLoader imgLoader = spimData2.getSequenceDescription().getImgLoader();
        List<ViewDescription> viewDescriptions = new ArrayList<ViewDescription>();
        viewDescriptions.addAll(spimData2.getSequenceDescription().getViewDescriptions().values());

        DoGParameters dog = new DoGParameters(viewDescriptions, imgLoader, 1.4, 2);

        String label = "ips";

        //todo add parameters for segmentation
        InterestPointTools.addInterestPoints(spimData2, label, DoG.findInterestPoints(dog),"");

        RemoveDetectionParameters removeDetectionParameters = new RemoveDetectionParameters();

        removeDetectionParameters.toProcess = new ArrayList<ViewDescription>();
        removeDetectionParameters.toProcess.addAll(spimData2.getSequenceDescription().getViewDescriptions().values());
        removeDetectionParameters.label = label;
        removeDetectionParameters.min = 0;
        removeDetectionParameters.max = 10;
        removeDetectionParameters.keepRange = false;

        HashMap<ViewId, List<InterestPoint>> newInterestPoints = DetectionRemoval.removeDetections(spimData2,removeDetectionParameters);

        String parameters = DetectionRemoval.getParameterString(removeDetectionParameters,label);
        InterestPointTools.addInterestPoints(spimData2, "thinned_" + label, newInterestPoints,parameters,true);

        SpimData2.saveXML(spimData2,"one.xml","");

    }


}
