package spim.fiji.plugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.boundingbox.BoundingBoxTools;
import spim.process.export.AppendSpimData2;
import spim.process.export.DisplayImage;
import spim.process.export.ExportSpimData2HDF5;
import spim.process.export.ExportSpimData2TIFF;
import spim.process.export.ImgExport;
import spim.process.export.Save3dTIFF;
import spim.process.fusion.FusionHelper;

/**
 * Plugin to fuse images using transformations from the SpimData object
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Image_Fusion implements PlugIn
{
	public static int defaultCache = 2;
	public static int[] cellDim = new int[]{ 10, 10, 10 };
	public static int maxCacheSize = 1000;

	public static double defaultDownsampling = 1.0;
	public static int defaultBB = 0;

	// TODO:
	public static String[] interpolationTypes = new String[]{ "Nearest Neighbor", "Linear Interpolation" };
	public static int defaultInterpolation = 1;
	protected int interpolation = 1;

	// TODO:
	public static boolean defaultUseBlending = true;
	protected boolean useBlending = true;

	// TODO:
	public static boolean defaultUseContentBased = false;
	protected boolean useContentBased = false;

	// TODO:
	public final static ArrayList< ImgExport > staticImgExportAlgorithms = new ArrayList< ImgExport >();
	public final static String[] imgExportDescriptions;
	public static int defaultImgExportAlgorithm = 0;

	static
	{
		IOFunctions.printIJLog = true;

		staticImgExportAlgorithms.add( new DisplayImage() );
		staticImgExportAlgorithms.add( new Save3dTIFF( null ) );
		staticImgExportAlgorithms.add( new ExportSpimData2TIFF() );
		staticImgExportAlgorithms.add( new ExportSpimData2HDF5() );
		staticImgExportAlgorithms.add( new AppendSpimData2() );

		imgExportDescriptions = new String[ staticImgExportAlgorithms.size() ];

		for ( int i = 0; i < staticImgExportAlgorithms.size(); ++i )
			imgExportDescriptions[ i ] = staticImgExportAlgorithms.get( i ).getDescription();
	}

	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		fuse( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public boolean fuse(
			final SpimData2 spimData,
			final List< ViewId > views )
	{
		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, views );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// which timepoints are part of the fusion
		final List< TimePoint > timepointToProcess = SpimData2.getAllTimePointsSorted( spimData, views );
		final int nAllTimepoints = spimData.getSequenceDescription().getTimePoints().size();

		// which channels are part of the fusion
		final List< Channel > channelsToProcess = SpimData2.getAllChannelsSorted( spimData, views );

		// get all bounding boxes and two extra ones
		final List< BoundingBox > allBoxes = BoundingBoxTools.getAllBoundingBoxes( spimData, views, true );
		final String[] choices = new String[ allBoxes.size() ];

		int i = 0;
		for ( final BoundingBox b : allBoxes )
			choices[ i++ ] = b.getTitle() + " [" + b.dimension( 0 ) + "x" + b.dimension( 1 ) + "x" + b.dimension( 2 ) + "px]";

		if ( defaultBB >= choices.length )
			defaultBB = 0;

		final GenericDialog gd = new GenericDialog( "Image Fusion" );

		gd.addChoice( "Fused_image", imgExportDescriptions, imgExportDescriptions[ defaultImgExportAlgorithm ] );
		gd.addMessage( "" );
		gd.addChoice( "Bounding_Box", choices, choices[ defaultBB ] );
		gd.addSlider( "Downsampling", 1.0, 16.0, defaultDownsampling );
		gd.addChoice( "Caching", FusionHelper.imgDataTypeChoice, FusionHelper.imgDataTypeChoice[ defaultCache ] );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;
/*
		bb = allBoxes.get( defaultBB = gd.getNextChoiceIndex() );
		downsampling = defaultDownsampling = gd.getNextNumber();
		int caching = defaultCache = gd.getNextChoiceIndex();

		if ( caching == 0 )
			imgType = ImgDataType.VIRTUAL;
		else if ( caching == 1 )
			imgType = ImgDataType.CACHED;
		else
			imgType = ImgDataType.PRECOMPUTED;
	*/
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

		new Image_Fusion().run( null );
	}
}
