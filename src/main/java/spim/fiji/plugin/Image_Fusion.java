package spim.fiji.plugin;

import java.util.Date;
import java.util.List;

import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.fusion.FusionGUI;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

/**
 * Plugin to fuse images using transformations from the SpimData object
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Image_Fusion implements PlugIn
{
	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		fuse( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean fuse(
			final SpimData2 spimData,
			final List< ViewId > views )
	{
		final FusionGUI fusion = new FusionGUI( spimData, views );

		if ( !fusion.queryDetails() )
			return false;

		final List< Group< ViewDescription > > groups = fusion.getFusionGroups();
		int i = 0;

		for ( final Group< ViewDescription > group : groups )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusing group " + (++i) + "/" + groups.size() + " (group=" + group + ")" );

			/*
	public int getPixelType() { return pixelType; }
	public int getCacheType() { return cacheType; }

			 */
			final RandomAccessibleInterval< FloatType > virtual =
				FusionTools.fuseVirtual(
					spimData,
					views,
					fusion.useBlending(),
					fusion.useContentBased(),
					fusion.getInterpolation(),
					fusion.getBoundingBox(),
					fusion.getDownsampling() );

			if ( fusion.getPixelType() == 1 ) // 16 bit
			{
				
			}
	
			
		}

		return true;
	}

	public static void fuseGroup(
			final SpimData2 spimData,
			final Group< ViewDescription > group
			)
	{
		
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Image_Fusion().run( null );
	}
}
