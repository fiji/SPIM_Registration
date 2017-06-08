package spim.headless.deconvolution;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.deconvolution.ProcessInputImages;
import spim.process.deconvolution.ProcessInputImages.ImgDataType;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.FusedWeightsRandomAccessibleInterval;
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

		final ProcessInputImages< ViewId > fusion = new ProcessInputImages<>(
				spimData,
				Group.toGroup( viewIds ),
				boundingBox,
				2.0,
				true,
				true );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Virtual Fusion of groups " );
		fusion.fuseGroups();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): De-virtualization ... " );
		fusion.deVirtualizeImages( ImgDataType.PRECOMPUTED, ImgDataType.CACHED );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Displaying " );
		displayDebug( fusion );
	}

	public static < V extends ViewId > void displayDebug( final ProcessInputImages< V > fusion )
	{
		int i = 0;

		for ( final Group< V > group : fusion.getImgWeights().keySet() )
		{
			System.out.println( "Img Instance: " + fusion.getImgWeights().get( group ).getA().getClass().getSimpleName() );
			System.out.println( "Weight Instance: " + fusion.getImgWeights().get( group ).getB().getClass().getSimpleName() );

			DisplayImage.getImagePlusInstance( fusion.getImgWeights().get( group ).getA(), true, "g=" + i + " image", 0, 255 ).show();
			DisplayImage.getImagePlusInstance( fusion.getImgWeights().get( group ).getB(), true, "g=" + i + " weightsDecon", 0, 2 ).show();

			if ( FusedRandomAccessibleInterval.class.isInstance( fusion.getImgWeights().get( group ).getA() ) )
			{
				final long[] dim = new long[ fusion.getDownsampledBoundingBox().numDimensions() ];
				fusion.getDownsampledBoundingBox().dimensions( dim );
	
				DisplayImage.getImagePlusInstance(
						new FusedWeightsRandomAccessibleInterval(
								new FinalInterval( dim ),
								((FusedRandomAccessibleInterval)fusion.getImgWeights().get( group ).getA()).getWeights() ),
						true,
						"g=" + i + " weightsFusion",
						0, 1 ).show();
			}

			++i;
		}
	}
}
