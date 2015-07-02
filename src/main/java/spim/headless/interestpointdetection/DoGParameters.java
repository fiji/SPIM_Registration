package spim.headless.interestpointdetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDASeparableConvolution;

public class DoGParameters extends InterestPointParameters
{
	public double imageSigmaX = 0.5;
	public double imageSigmaY = 0.5;
	public double imageSigmaZ = 0.5;

	/**
	 * 0 = no subpixel localization
	 * 1 = quadratic fit
	 */
	public int localization = 1;

	public double sigma = 1.8;
	public double threshold = 0.01;
	public boolean findMin = false;
	public boolean findMax = true;

	public double percentGPUMem = 75;
	public ArrayList< CUDADevice > deviceList = null;
	public CUDASeparableConvolution cuda = null;
	public boolean accurateCUDA = false;

    public DoGParameters(){
        super();
    }

    public DoGParameters(Collection<ViewDescription> toProcess, ImgLoader<?> imgloader, double sigma, double threshold) {
        super(toProcess, imgloader);
        this.sigma = sigma;
        this.threshold = threshold;
    }


    public DoGParameters(Collection<ViewDescription> toProcess, ImgLoader<?> imgloader, double sigma, int downsampleXY)
    {
        super(toProcess,imgloader);
        this.sigma = sigma;
        this.downsampleXY = downsampleXY;
    }


    public static void main( String[] args )
	{
		SpimData spimData = SimulatedBeadsImgLoader.spimdataExample();
		DoGParameters dog = new DoGParameters();

		dog.imgloader = spimData.getSequenceDescription().getImgLoader();
		dog.toProcess = new ArrayList< ViewDescription >();
		dog.toProcess.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		dog.downsampleXY = 2;
		dog.sigma = 1.4;

		//dog.deviceList = spim.headless.cuda.CUDADevice.getSeparableCudaList( "lib/libSeparableConvolutionCUDALib.so" );
		//dog.cuda = spim.headless.cuda.CUDADevice.separableConvolution;

		DoG.findInterestPoints( dog );
	}
}
