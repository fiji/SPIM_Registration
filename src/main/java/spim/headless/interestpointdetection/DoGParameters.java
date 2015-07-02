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

	public static void testDoG( SpimData2 spimData )
	{
		DoGParameters dog = new DoGParameters();

		dog.imgloader = spimData.getSequenceDescription().getImgLoader();
		dog.toProcess = new ArrayList< ViewDescription >();
		dog.toProcess.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		dog.downsampleXY = 2;
		dog.sigma = 1.4;

		// TODO: make cuda headless
		//dog.deviceList = spim.headless.cuda.CUDADevice.getSeparableCudaList( "lib/libSeparableConvolutionCUDALib.so" );
		//dog.cuda = spim.headless.cuda.CUDADevice.separableConvolution;

		final HashMap< ViewId, List< InterestPoint > > points = DoG.findInterestPoints( dog );

		InterestPointTools.addInterestPoints( spimData, "beads", points, "DoG, sigma=1.4, downsample=2" );
	}

	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample() );

		testDoG( spimData );
	}
}
