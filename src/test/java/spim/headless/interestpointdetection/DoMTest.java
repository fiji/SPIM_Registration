package spim.headless.interestpointdetection;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewDescription;

import simulation.imgloader.SimulatedBeadsImgLoader;

import java.util.ArrayList;
import org.junit.Test;

/**
 * DifferenceOfMean Test class
 */
public class DoMTest
{
	@Test
	public void DoMParameterTest()
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
