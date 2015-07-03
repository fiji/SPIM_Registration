package spim.headless.interestpointdetection;

import mpicbg.spim.data.sequence.ViewDescription;

import mpicbg.spim.data.sequence.ViewId;
import simulation.imgloader.SimulatedBeadsImgLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;

/**
 * DifferenceOfGaussian Test class
 */
public class DoGTest
{
	@Test
	public void DoGParameterTest()
	{
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample() );

		DoGParameters dog = new DoGParameters();

		dog.imgloader = spimData.getSequenceDescription().getImgLoader();
		dog.toProcess = new ArrayList< ViewDescription >();
		dog.toProcess.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		dog.downsampleXY = 2;
		dog.sigma = 1.4;

//		dog.deviceList = spim.headless.cuda.CUDADevice.getSeparableCudaList( "lib/libSeparableConvolutionCUDALib.so" );
//		dog.cuda = spim.headless.cuda.CUDADevice.separableConvolution;

		final HashMap< ViewId, List< InterestPoint > > points = DoG.findInterestPoints( dog );

		InterestPointTools.addInterestPoints( spimData, "beads", points, "DoG, sigma=1.4, downsample=2" );
	}
}
