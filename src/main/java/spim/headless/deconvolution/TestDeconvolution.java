package spim.headless.deconvolution;

import java.util.ArrayList;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.deconvolution.ProcessInputImages;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestDeconvolution
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;

		// generate 4 views with 1000 corresponding beads, single timepoint
		spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );
		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testDeconvolution( spimData, "My Bounding Box" );
	}

	public static void testDeconvolution( final SpimData2 spimData, final String bbTitle )
	{
		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		for ( int i = 0; i < 5; ++i )
			viewIds.remove( viewIds.size() - 1 );

		BoundingBox boundingBox = null;

		for ( final BoundingBox bb : spimData.getBoundingBoxes().getBoundingBoxes() )
			if ( bb.getTitle().equals( bbTitle ) )
				boundingBox = bb;

		if ( boundingBox == null )
		{
			System.out.println( "Bounding box '" + bbTitle + "' not found." );
			return;
		}

		System.out.println( BoundingBox.getBoundingBoxDescription( boundingBox ) );

		ProcessInputImages.preProcessVirtual(
				spimData,
				Group.toGroups( viewIds ),
				boundingBox,
				4.0 );
	}
}
