package spim.headless.interestpointdetection;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by schmied on 01/07/15.
 */
public class DoMParameters extends InterestPointParameters
{
    /**
     * 0 = no subpixel localization
     * 1 = quadratic fit
     */
	public int localization = 1;
	public double imageSigmaX = 0.5;
    public double imageSigmaY = 0.5;
    public double imageSigmaZ = 0.5;

    public int radius1 = 2;
    public int radius2 = 3;
    public float threshold = (float) 0.005;
    public boolean findMin = false;
    public boolean findMax = true;

    public double minIntensity;
    public double maxIntensity;

    public static void main( String[] args )
    {
        SpimData spimData = SimulatedBeadsImgLoader.spimdataExample();
        public final String xmlFilename = "/Users/schmied";
        DoMParameters dom = new DoMParameters();

        dom.imgloader = spimData.getSequenceDescription().getImgLoader();
        dom.toProcess = new ArrayList< ViewDescription >();
        dom.toProcess.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

        dom.downsampleXY = 1;

        ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
        viewInterestPoints.createViewInterestPoints(spimData.getSequenceDescription().getViewDescriptions());
        final SpimData2 spimData2 = new SpimData2(new File(xmlFilename), spimData.getSequenceDescription(), spimData.getViewRegistrations(), viewInterestPoints, new BoundingBoxes());


        InterestPointTools.addInterestPoints(spimData2, label, DoM.findInterestPoints( dom ), "" );

        spimData2.saveXML(spimData2, "one.xml", "");
    }
}
