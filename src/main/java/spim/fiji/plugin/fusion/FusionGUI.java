package spim.fiji.plugin.fusion;

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.boundingbox.BoundingBoxTools;
import spim.process.export.AppendSpimData2HDF5;
import spim.process.export.DisplayImage;
import spim.process.export.ExportSpimData2HDF5;
import spim.process.export.ExportSpimData2TIFF;
import spim.process.export.ImgExport;
import spim.process.export.Save3dTIFF;
import spim.process.fusion.FusionTools;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

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

	public static String[] splittingTypes = new String[]{
			"Each timepoint & channel",
			"Each timepoint, channel & illumination",
			"All views together",
			"Each view" };

	public static int defaultSplittingType = 0;

	public static boolean defaultUseBlending = true;
	public static boolean defaultUseContentBased = false;

	public final static ArrayList< ImgExport > staticImgExportAlgorithms = new ArrayList< ImgExport >();
	public final static String[] imgExportDescriptions;
	public static int defaultImgExportAlgorithm = 0;

	protected int interpolation = defaultInterpolation;
	protected int boundingBox = defaultBB;
	protected int pixelType = defaultPixelType;
	protected int cacheType = defaultCache;
	protected int splittingType = defaultSplittingType;
	protected double downsampling = defaultDownsampling;
	protected boolean useBlending = defaultUseBlending;
	protected boolean useContentBased = defaultUseContentBased;
	protected int imgExport = defaultImgExportAlgorithm;

	static
	{
		IOFunctions.printIJLog = true;

		staticImgExportAlgorithms.add( new DisplayImage() );
		staticImgExportAlgorithms.add( new Save3dTIFF( null ) );
		staticImgExportAlgorithms.add( new ExportSpimData2TIFF() );
		staticImgExportAlgorithms.add( new ExportSpimData2HDF5() );
		staticImgExportAlgorithms.add( new AppendSpimData2HDF5() );

		imgExportDescriptions = new String[ staticImgExportAlgorithms.size() ];

		for ( int i = 0; i < staticImgExportAlgorithms.size(); ++i )
			imgExportDescriptions[ i ] = staticImgExportAlgorithms.get( i ).getDescription();
	}

	final protected SpimData2 spimData;
	final List< ViewId > views;
	final List< BoundingBox > allBoxes;

	public FusionGUI( final SpimData2 spimData, final List< ViewId > views )
	{
		this.spimData = spimData;
		this.views = new ArrayList<>();
		this.views.addAll( views );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, views );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// get all bounding boxes and two extra ones
		this.allBoxes = BoundingBoxTools.getAllBoundingBoxes( spimData, views, true );
	}

	public SpimData2 getSpimData() { return spimData; }
	public List< ViewId > getViews() { return views; }
	public Interval getBoundingBox() { return allBoxes.get( boundingBox ); }
	public Interval getDownsampledBoundingBox()
	{
		if ( !Double.isNaN( downsampling ) )
			return TransformVirtual.scaleBoundingBox( getBoundingBox(), 1.0 / downsampling );
		else
			return getBoundingBox();
	}
	public int getInterpolation() { return interpolation; }
	public int getPixelType() { return pixelType; }
	public int getCacheType() { return cacheType; }
	public double getDownsampling(){ return downsampling; }
	public boolean useBlending() { return useBlending; }
	public boolean useContentBased() { return useContentBased; }
	public int getSplittingType() { return splittingType; }
	public ImgExport getExporter() { return staticImgExportAlgorithms.get( imgExport ).newInstance(); }

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
		gd.addChoice( "Image ", FusionTools.imgDataTypeChoice, FusionTools.imgDataTypeChoice[ defaultCache ] );
		gd.addMessage( "We advise to use use VIRTUAL for saving at TIFF, and CACHED for saving as HDF5 if memory is low", GUIHelper.smallStatusFont, GUIHelper.neutral );
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

		boundingBox = defaultBB = gd.getNextChoiceIndex();
		downsampling = defaultDownsampling = gd.getNextNumber();
		pixelType = defaultPixelType = gd.getNextChoiceIndex();
		interpolation = defaultInterpolation = gd.getNextChoiceIndex();
		cacheType = defaultCache = gd.getNextChoiceIndex();
		useBlending = defaultUseBlending = gd.getNextBoolean();
		useContentBased = defaultUseContentBased = gd.getNextBoolean();
		splittingType = defaultSplittingType = gd.getNextChoiceIndex();
		imgExport = defaultImgExportAlgorithm = gd.getNextChoiceIndex();

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Selected Fusion Parameters: " );
		IOFunctions.println( "Downsampling: " + getDownsampling() );
		IOFunctions.println( "BoundingBox: " + getBoundingBox() );
		IOFunctions.println( "DownsampledBoundingBox: " + getDownsampledBoundingBox() );
		IOFunctions.println( "PixelType: " + pixelTypes[ getPixelType() ] );
		IOFunctions.println( "Interpolation: " + interpolationTypes[ getInterpolation() ] );
		IOFunctions.println( "CacheType: " + FusionTools.imgDataTypeChoice[ getCacheType() ] );
		IOFunctions.println( "Blending: " + useBlending );
		IOFunctions.println( "Content-based: " + useContentBased );
		IOFunctions.println( "Split by: " + splittingTypes[ getSplittingType() ] );
		IOFunctions.println( "Image Export: " + imgExportDescriptions[ imgExport ] );
		IOFunctions.println( "ImgLoader.isVirtual(): " + isImgLoaderVirtual() );
		IOFunctions.println( "ImgLoader.isMultiResolution(): " + isMultiResolution() );

		return true;
	}

	public boolean isImgLoaderVirtual()
	{
		if ( MultiResolutionImgLoader.class.isInstance( spimData.getSequenceDescription().getImgLoader() ) )
			return true;

		// TODO: check for Davids virtual implementation of the normal imgloader
		return false;
	}

	public boolean isMultiResolution()
	{
		if ( MultiResolutionImgLoader.class.isInstance( spimData.getSequenceDescription().getImgLoader() ) )
			return true;
		else
			return false;
	}

	public List< Group< ViewDescription > > getFusionGroups()
	{
		final ArrayList< ViewDescription > vds = SpimData2.getAllViewDescriptionsSorted( this.spimData, this.views );
		final List< Group< ViewDescription > > grouped;

		if ( this.splittingType < 2 ) // "Each timepoint & channel" or "Each timepoint, channel & illumination"
		{
			final HashSet< Class< ? extends Entity > > groupingFactors = new HashSet<>();

			groupingFactors.add( TimePoint.class );
			groupingFactors.add( Channel.class );

			if ( this.splittingType == 1 ) // "Each timepoint, channel & illumination"
				groupingFactors.add( Illumination.class );

			grouped = Group.splitBy( vds, groupingFactors );
		}
		else if ( this.splittingType == 2 ) // "All views together"
		{
			final Group< ViewDescription > allViews = new Group<>( vds );
			grouped = new ArrayList<>();
			grouped.add( allViews );
		}
		else // "All views"
		{
			grouped = new ArrayList<>();
			for ( final ViewDescription vd : vds )
				grouped.add( new Group<>( vd ) );
		}

		return grouped;
	}

	public long maxNumInputPixelsPerInputGroup()
	{
		long maxNumPixels = 0;

		for ( final Group< ViewDescription > group : getFusionGroups() )
		{
			long numpixels = 0;

			for ( final ViewDescription vd : group )
				numpixels += Intervals.numElements( vd.getViewSetup().getSize() );

			maxNumPixels = Math.max( maxNumPixels, numpixels );
		}

		return maxNumPixels;
	}
}
