package spim.headless.boundingbox;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.array.ArrayImgFactory;
import spim.fiji.plugin.boundingbox.MinFilterThresholdBoundingBoxGUI;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.boundingbox.BoundingBoxMinFilterThreshold;
import spim.process.fusion.FusionTools;

public class TestRealDataBoundingBox
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		// test a real scenario
		final SpimData2 spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );;

		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		System.out.println( viewIds.size() + " views in total." );

		final BoundingBoxMinFilterThreshold estimation = new BoundingBoxMinFilterThreshold(
				spimData,
				viewIds,
				new ArrayImgFactory<>(),
				MinFilterThresholdBoundingBoxGUI.defaultBackgroundIntensity,
				MinFilterThresholdBoundingBoxGUI.defaultDiscardedObjectSize,
				true,
				8 );

		final BoundingBox bb = estimation.estimate( "MinFilterThresholdBoundingBoxGUI" );

		FusionTools.displayCopy( FusionTools.fuseVirtual( spimData, viewIds, true, bb, 2.0 ), estimation.getMinIntensity(), estimation.getMaxIntensity() ).show();
	}
}
