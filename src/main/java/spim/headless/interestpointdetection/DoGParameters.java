package spim.headless.interestpointdetection;

import java.util.ArrayList;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewDescription;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDASeparableConvolution;

public class DoGParameters extends InterestPointParameters
{
	protected double imageSigmaX = 0.5;
	protected double imageSigmaY = 0.5;
	protected double imageSigmaZ = 0.5;

	/**
	 * 0 = no subpixel localization
	 * 1 = quadratic fit
	 */
	protected int localization = 1;

	double sigma = 1.8;
	double threshold = 0.01;
	boolean findMin = false;
	boolean findMax = true;

	double percentGPUMem = 75;
	ArrayList< CUDADevice > deviceList = null;
	CUDASeparableConvolution cuda = null;
	boolean accurateCUDA = false;

	public static void main( String[] args )
	{
		SpimData spimData = SimulatedBeadsImgLoader.spimdataExample();

		DoGParameters dog = new DoGParameters();

		dog.imgloader = spimData.getSequenceDescription().getImgLoader();
		dog.toProcess = new ArrayList< ViewDescription >();
		dog.toProcess.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		dog.downsampleXY = 2;
		dog.sigma = 1.4;

		dog.deviceList = spim.headless.cuda.CUDADevice.getSeparableCudaList( "lib/libSeparableConvolutionCUDALib.so" );
		dog.cuda = spim.headless.cuda.CUDADevice.separableConvolution;

		DoG.findInterestPoints( dog );
	}
}
