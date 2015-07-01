package spim.headless.interestpointdetection;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewDescription;
import simulation.imgloader.SimulatedBeadsImgLoader;

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
    protected int localization = 1;

    protected double imageSigmaX = 0.5;
    protected double imageSigmaY = 0.5;
    protected double imageSigmaZ = 0.5;

    int radius1 = 2;
    int radius2 = 3;
    float threshold = (float) 0.005;
    boolean findMin = false;
    boolean findMax = true;

    double minIntensity;
    double maxIntensity;

    public static void main( String[] args )
    {
        SpimData spimData = SimulatedBeadsImgLoader.spimdataExample();

        DoMParameters dom = new DoMParameters();

        dom.imgloader = spimData.getSequenceDescription().getImgLoader();
        dom.toProcess = new ArrayList< ViewDescription >();
        dom.toProcess.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

        dom.downsampleXY = 1;

        DoM.findInterestPoints( dom );
    }
}
