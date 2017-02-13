package spim.headless.interestpointdetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;

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

	public int radius1 = 2;
	public int radius2 = 3;
	public float threshold = (float) 0.005;
	public boolean findMin = false;
	public boolean findMax = true;

	public static void testDoM( final SpimData2 spimData )
	{
		DoMParameters dom = new DoMParameters();
		
		dom.imgloader = spimData.getSequenceDescription().getImgLoader();
		dom.toProcess = new ArrayList< ViewDescription >();
		dom.toProcess.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );
		
		dom.downsampleXY = 2;
		dom.radius1 = 2;
		
		final HashMap< ViewId, List< InterestPoint > > points = DoM.findInterestPoints( dom );
		
		InterestPointTools.addInterestPoints( spimData, "beads", points, "DoM, sigma=2, downsample=2" );
	}

	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample() );
		
		testDoM( spimData );
	}
}
