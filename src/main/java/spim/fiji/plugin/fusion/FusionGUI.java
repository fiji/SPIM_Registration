package spim.fiji.plugin.fusion;

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Intervals;
import spim.fiji.plugin.boundingbox.BoundingBoxGUI;
import spim.fiji.plugin.boundingbox.BoundingBoxGUI.ManageListeners;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.boundingbox.BoundingBoxTools;
import spim.process.export.AppendSpimData2;
import spim.process.export.DisplayImage;
import spim.process.export.ExportSpimData2HDF5;
import spim.process.export.ExportSpimData2TIFF;
import spim.process.export.ImgExport;
import spim.process.export.Save3dTIFF;
import spim.process.fusion.FusionHelper;

public class FusionGUI
{
	public static int defaultCache = 2;
	public static int[] cellDim = new int[]{ 10, 10, 10 };
	public static int maxCacheSize = 1000;

	public static double defaultDownsampling = 1.0;
	public static int defaultBB = 0;

	public static String[] interpolationTypes = new String[]{ "Nearest Neighbor", "Linear Interpolation" };
	public static int defaultInterpolation = 1;

	public static String[] pixelTypes = new String[]{ "32-bit floating point", "16-bit unsigned integer" };
	public static int defaultPixelType = 0;

	public static String[] splittingTypes = new String[]{ "Each timepoint & channel", "All views together", "Each view", "Specify in more detail ..." };
	public static int defaultSplittingType = 0;

	public static boolean defaultUseBlending = true;
	public static boolean defaultUseContentBased = false;

	public final static ArrayList< ImgExport > staticImgExportAlgorithms = new ArrayList< ImgExport >();
	public final static String[] imgExportDescriptions;
	public static int defaultImgExportAlgorithm = 0;

	protected int interpolation = 1;
	protected int boundingBox = defaultBB;
	protected int pixelType = defaultPixelType;
	protected int cacheType = defaultCache;
	protected int splittingType = defaultSplittingType;
	protected double downsampling = defaultDownsampling;
	protected boolean useBlending = defaultUseBlending;
	protected boolean useContentBased = defaultUseContentBased;

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

	final protected SpimData2 spimData;
	final List< ViewId > views;

	final List< TimePoint > timepointsToProcess;
	final List< Channel > channelsToProcess;
	final List< BoundingBox > allBoxes;

	public FusionGUI( final SpimData2 spimData, final List< ViewId > views )
	{
		this.spimData = spimData;
		this.views = new ArrayList<>();
		this.views.addAll( views );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, views );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// which timepoints are part of the fusion
		this.timepointsToProcess = SpimData2.getAllTimePointsSorted( spimData, views );

		// which channels are part of the fusion
		this.channelsToProcess = SpimData2.getAllChannelsSorted( spimData, views );

		// get all bounding boxes and two extra ones
		this.allBoxes = BoundingBoxTools.getAllBoundingBoxes( spimData, views, true );
	}

	public boolean queryDetails()
	{
		final String[] choices = new String[ allBoxes.size() ];

		int i = 0;
		for ( final BoundingBox b : allBoxes )
			choices[ i++ ] = b.getTitle() + " [" + b.dimension( 0 ) + "x" + b.dimension( 1 ) + "x" + b.dimension( 2 ) + "px]";

		if ( defaultBB >= choices.length )
			defaultBB = 0;

		final GenericDialog gd = new GenericDialog( "Image Fusion" );

		gd.addChoice( "Bounding_Box", choices, choices[ defaultBB ] );

		gd.addMessage( "" );

		gd.addSlider( "Downsampling", 1.0, 16.0, defaultDownsampling );
		gd.addChoice( "Pixel_type", pixelTypes, pixelTypes[ defaultPixelType ] );
		gd.addChoice( "Interpolation", interpolationTypes, interpolationTypes[ defaultInterpolation ] );
		gd.addChoice( "Image ", FusionHelper.imgDataTypeChoice, FusionHelper.imgDataTypeChoice[ defaultCache ] );
		gd.addMessage( "For saving at TIFF use virtual, for saving as HDF5 use Cached", GUIHelper.smallStatusFont, GUIHelper.neutral );
		gd.addMessage( "" );

		gd.addCheckbox( "Blend images smoothly", defaultUseBlending );
		gd.addCheckbox( "Use content based fusion (warning, huge memory requirements)", defaultUseContentBased );

		gd.addMessage( "" );

		gd.addChoice( "Produce one fused image for", splittingTypes, splittingTypes[ defaultSplittingType ] );
		gd.addChoice( "Fused_image", imgExportDescriptions, imgExportDescriptions[ defaultImgExportAlgorithm ] );

		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		Label label1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		Label label2 = (Label)gd.getMessage();

		final ManageFusionDialogListeners m = new ManageFusionDialogListeners(
				gd,
				(Choice)gd.getChoices().get( 0 ),
				(TextField)gd.getNumericFields().get( 0 ),
				(Choice)gd.getChoices().get( 1 ),
				(Choice)gd.getChoices().get( 3 ),
				(Checkbox)gd.getCheckboxes().get( 1 ),
				(Choice)gd.getChoices().get( 4 ),
				label1,
				label2,
				this );

		m.update();

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

	public boolean isImgLoaderVirtual()
	{
		if ( MultiResolutionImgLoader.class.isInstance( spimData.getSequenceDescription().getImgLoader() ) )
			return true;

		// TODO: check for Davids virtual implementation of the normal imgloader

		return false;
	}

	protected long computeAvgImageSize()
	{
		long avgSize = 0;
		int countImgs = 0;

		for ( final ViewId viewId : views  )
		{
			final ViewDescription desc = spimData.getSequenceDescription().getViewDescription( viewId );

			if ( desc.isPresent() )
			{
				final ViewSetup viewSetup = desc.getViewSetup();
				final long numPixel = Intervals.numElements( ViewSetupUtils.getSizeOrLoad( viewSetup, desc.getTimePoint(), spimData.getSequenceDescription().getImgLoader() ) );

				avgSize += numPixel;
				++countImgs;
			}
		}
		
		return avgSize / countImgs;
	}

	/*
	 * @return - max num views per fused image
	 */
	protected int computeMaxNumViews()
	{
		int maxViews = 0;
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
			{
				int numViews = 0;

				for ( final ViewId viewId : views )
				{
					final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
					
					if ( vd.isPresent() && vd.getTimePointId() == t.getId() && vd.getViewSetup().getChannel().getId() == c.getId() )
						++numViews;
				}
				
				maxViews = Math.max( maxViews, numViews );
			}
		
		return maxViews;
	}
}
