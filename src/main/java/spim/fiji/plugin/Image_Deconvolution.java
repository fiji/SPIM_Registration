package spim.fiji.plugin;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.Threads;
import spim.fiji.plugin.fusion.DeconvolutionGUI;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;

/**
 * Plugin to fuse images using transformations from the SpimData object
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Image_Deconvolution implements PlugIn
{
	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset (Multiview) Deconvolution", true, true, true, true, true ) )
			return;

		deconvolve( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean deconvolve(
			final SpimData2 spimData,
			final List< ViewId > views )
	{
		return deconvolve( spimData, views, Executors.newFixedThreadPool( Threads.numThreads() ) );
	}

	public static boolean deconvolve(
			final SpimData2 spimData,
			final List< ViewId > views,
			final ExecutorService service )
	{
		final DeconvolutionGUI decon = new DeconvolutionGUI( spimData, views, service );

		if ( !decon.queryDetails() )
			return false;


		return true;
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Image_Deconvolution().run( null );
	}
}
