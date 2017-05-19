package spim.headless.boundingbox;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.headless.registration.TestRegistration;
import spim.process.boundingbox.BoundingBoxEstimation;
import spim.process.boundingbox.MaximumBoundingBox;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestBoundingBox
{
	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testBoundingBox( spimData );
	}

	public static void testBoundingBox( final SpimData2 spimData )
	{
		// run the whole pipeline
		TestRegistration.testRegistration( spimData, false );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		final BoundingBoxEstimation estimation = new MaximumBoundingBox( viewIds, spimData );

		final BoundingBox bb = estimation.estimate( "Full Bounding Box" );

		System.out.println( bb );
	}
}
