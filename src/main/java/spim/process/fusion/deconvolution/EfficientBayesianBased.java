package spim.process.fusion.deconvolution;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.GenericDialog;

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.imglib.util.Util;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDAFourierConvolution;
import spim.process.cuda.CUDATools;
import spim.process.cuda.NativeLibraryTools;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.deconvolution.MVDeconFFT.PSFTYPE;
import spim.process.fusion.deconvolution.ProcessForDeconvolution.WeightType;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.boundingbox.BoundingBoxGUI.ManageListeners;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.export.FixedNameImgTitler;
import spim.process.fusion.export.ImgExport;
import spim.process.fusion.export.ImgExportTitle;
import spim.process.fusion.weightedavg.WeightedAverageFusion;

public class EfficientBayesianBased extends Fusion
{
	public static String[] computationOnChoice = new String[]{ "CPU (Java)", "GPU (Nvidia CUDA via JNA)" };
	public static String[] osemspeedupChoice = new String[]{ "1 (balanced)", "minimal number of overlapping views", "average number of overlapping views", "specify manually" };
	public static String[] extractPSFChoice = new String[]{ "Extract from beads", "Provide file with PSF" };
	public static String[] blocksChoice = new String[]{ "Entire image at once", "in 64x64x64 blocks", "in 128x128x128 blocks", "in 256x256x256 blocks", "in 512x512x512 blocks", "specify maximal blocksize manually" };
	public static String[] displayPSFChoice = new String[]{ "Do not show PSFs", "Show MIP of combined PSF's", "Show combined PSF's", "Show individual PSF's", "Show combined PSF's (original scale)", "Show individual PSF's (original scale)" };
	public static String[] iterationTypeString = new String[]{
		"Efficient Bayesian - Optimization II (very fast, imprecise)", 
		"Efficient Bayesian - Optimization I (fast, precise)", 
		"Efficient Bayesian (less fast, more precise)", 
		"Independent (slow, very precise)" };

	public static String[] weightsString = new String[]{
		"Precompute weights for all views (more memory, faster)",
		"Virtual weights (less memory, slower)",
		"No weights (produces artifacts on partially overlapping data)",
		"Illustrate overlap of views per pixel (do not deconvolve)" };

	public static int defaultBlendingRangeNumber = 12;
	public static int defaultBlendingBorderNumber = -8;
	public static int[] defaultBlendingRange = null;
	public static int[] defaultBlendingBorder = null;

	public static boolean makeAllPSFSameSize = false;

	public static int defaultFFTImgType = 0;
	public static int defaultIterationType = 1;
	public static int defaultWeightType = 1;
	public static boolean defaultSaveMemory = false;
	public static int defaultOSEMspeedupIndex = 0;
	public static int defaultNumIterations = 10;
	public static boolean defaultUseTikhonovRegularization = true;
	public static double defaultLambda = 0.006;
	public static int defaultBlockSizeIndex = 0, defaultBlockSizeX = 256, defaultBlockSizeY = 256, defaultBlockSizeZ = 256;
	public static int defaultComputationTypeIndex = 0;
	public static int defaultExtractPSF = 0;
	public static int defaultDisplayPSF = 1;
	public static boolean defaultDebugMode = false;
	public static boolean defaultAdjustBlending = false;
	public static int defaultDebugInterval = 1;
	public static double defaultOSEMspeedup = 1;
	public static boolean defaultSamePSFForAllAnglesIllums = true;
	public static boolean defaultSamePSFForAllChannels = true;
	public static boolean defaultTransformPSFs = true;
	public static ArrayList< String > defaultPSFFileField = null;
	public static int[] defaultPSFLabelIndex = null;
	public static int defaultPSFSizeX = 19;
	public static int defaultPSFSizeY = 19;
	public static int defaultPSFSizeZ = 25;
	public static String defaultCUDAPath = null;
	public static boolean defaultCUDAPathIsRelative = true;

	PSFTYPE iterationType;
	WeightType weightType;
	boolean saveMemory;
	int osemspeedupIndex;
	int numIterations;
	boolean useTikhonovRegularization;
	double lambda;
	int blockSizeIndex;
	int computationTypeIndex;
	int extractPSFIndex;
	int displayPSF;
	boolean debugMode;
	boolean adjustBlending;

	// set in fuseData method
	ImgFactory< FloatType > factory;

	/**
	 * The ImgFactory used for Blocks and for computing the actual FFT ... should be ArrayImg if there is no good reason
	 */
	ImgFactory< FloatType > computeFactory = new ArrayImgFactory< FloatType >();

	boolean useBlocks;
	int[] blockSize;
	boolean useCUDA;
	int debugInterval;
	double osemSpeedUp;
	boolean extractPSF;
	boolean transformPSFs;
	HashMap< Channel, ArrayList< Pair< Pair< Angle, Illumination >, String > > > psfFiles;
	HashMap< Channel, ChannelPSF > extractPSFLabels; // should be either a String or another Channel object
	int blendingBorderX, blendingBorderY, blendingBorderZ;
	int blendingRangeX, blendingRangeY, blendingRangeZ;
	int psfSizeX = -1;
	int psfSizeY = -1;
	int psfSizeZ = -1;

	/**
	 * 0 ... n == CUDA device i
	 */
	ArrayList< CUDADevice > deviceList = null;

	Choice gpu, block, it, weight;
	Checkbox saveMem;

	public EfficientBayesianBased( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
		
		// we want the cellimg by default (better with memory, almost same speed)
		BoundingBoxGUI.defaultImgType = 2;
		
		// linear interpolation
		Fusion.defaultInterpolation = this.interpolation = 1;
	}

	@Override
	public boolean fuseData( final BoundingBoxGUI bb, final ImgExport exporter )
	{
		try
		{
			// set up naming scheme
			final FixedNameImgTitler titler = new FixedNameImgTitler( "" );
			if ( exporter instanceof ImgExportTitle )
				( (ImgExportTitle)exporter).setImgTitler( titler );
	
			// set up ImgFactory
			this.factory = bb.getImgFactory( new FloatType() );

			IOFunctions.println( "BlendingBorder: " + blendingBorderX + ", " + blendingBorderY + ", " + blendingBorderZ );
			IOFunctions.println( "BlendingBorder: " + blendingRangeX + ", " + blendingRangeY + ", " + blendingRangeZ );

			final ProcessForDeconvolution pfd = new ProcessForDeconvolution(
					spimData,
					viewIdsToProcess,
					bb,
					new int[]{ blendingBorderX, blendingBorderY, blendingBorderZ },
					new int[]{ blendingRangeX, blendingRangeY, blendingRangeZ } );
			
			// set debug mode
			MVDeconvolution.debug = debugMode;
			MVDeconvolution.debugInterval = debugInterval;
	
			int stack = 0;
	
			for ( final TimePoint t : timepointsToProcess )
				for ( final Channel c : channelsToProcess )
				{
					final List< Angle > anglesToProcess = SpimData2.getAllAnglesForChannelTimepointSorted( spimData, viewIdsToProcess, c, t );
					final List< Illumination > illumsToProcess = SpimData2.getAllIlluminationsForChannelTimepointSorted( spimData, viewIdsToProcess, c, t );
	
					// fuse the images, create weights, extract PSFs we need for the deconvolution
					if ( !pfd.fuseStacksAndGetPSFs(
							t, c,
							factory,
							osemspeedupIndex,
							osemSpeedUp,
							weightType,
							extractPSFLabels,
							new long[]{ psfSizeX, psfSizeY, psfSizeZ },
							psfFiles,
							transformPSFs ) )
					{
						IOFunctions.println(
								"FAILED to deconvolve timepoint=" + t.getName() + " (id=" + t.getId() + ")" +
								", channel=" + c.getName() + " (id=" + c.getId() + ")" );
	
						continue;
					}
					
					// on the first run update the osemspeedup if necessary
					if ( stack++ == 0 )
					{
						if ( osemspeedupIndex == 1 )
							osemSpeedUp = pfd.getMinOverlappingViews();
						else if ( osemspeedupIndex == 2 )
							osemSpeedUp = pfd.getAvgOverlappingViews();
					}
	
					// setup & run the deconvolution
					displayParametersAndPSFs( bb, c, extractPSFLabels );
	
					if ( weightType == WeightType.WEIGHTS_ONLY )
						return true;
	
					final MVDeconInput deconvolutionData = new MVDeconInput( factory );
	
					IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Block & FFT image factory: " + computeFactory.getClass().getSimpleName() );

					for ( final ViewDescription vd : pfd.getViewDescriptions() )
					{
						// device list for CPU or CUDA processing
						final int[] devList = new int[ deviceList.size() ];
						for ( int i = 0; i < devList.length; ++i )
							devList[ i ] = deviceList.get( i ).getDeviceId();
	
						deconvolutionData.add( new MVDeconFFT(
								pfd.getTransformedImgs().get( vd ),
								pfd.getTransformedWeights().get( vd ),
								pfd.getExtractPSF().getTransformedPSF( vd ),
								computeFactory, devList, useBlocks, blockSize, saveMemory ) );
					}
	
					if ( !useTikhonovRegularization )
						lambda = 0;
	
					final Img< FloatType > deconvolved;
	
					try
					{
						deconvolved = new MVDeconvolution( deconvolutionData, iterationType, numIterations, lambda, osemSpeedUp, osemspeedupIndex, "deconvolved" ).getPsi();
					} 
					catch (IncompatibleTypeException e)
					{
						IOFunctions.println( "Failed to initialize deconvolution: " + e );
						e.printStackTrace();
						return false;
					}
	
					// export the final image
					titler.setTitle( "TP" + t.getName() + "_Ch" + c.getName() + FusionHelper.getIllumName( illumsToProcess ) + FusionHelper.getAngleName( anglesToProcess ) );
					exporter.exportImage(
							deconvolved,
							bb,
							t,
							newViewsetups.get( SpimData2.getViewSetup( spimData.getSequenceDescription().getViewSetupsOrdered(), c, anglesToProcess.get( 0 ), illumsToProcess.get( 0 ) ) ),
							0, 1 );
				}
		}
		catch ( OutOfMemoryError oome )
		{
			IJ.log( "Out of Memory" );
			IJ.error("Multi-View Registration", "Out of memory.  Check \"Edit > Options > Memory & Threads\"");
			return false;
		}
		
		return true;
	}
	
	@Override
	public boolean queryParameters()
	{
		// check blocks
		if ( !getBlocks() )
			return false;
		
		// check CUDA
		if ( !getCUDA() )
			return false;

		// check PSF
		if ( !getPSF() )
			return false;
		
		// reorder the channels so that those who extract a PSF
		// from the images for a certain timepoint will be processed
		// first
		if ( extractPSF )
			if ( !reOrderChannels() )
				return false;
		
		// check OSEM
		if ( !getOSEM() )
			return false;
		
		// get the blending parameters
		if ( !getBlending( ) )
			return false;
		
		// check debug interval
		if ( !getDebug() )
			return false;

		return true;
	}
	
	@Override
	public void registerAdditionalListeners( final ManageListeners m )
	{
		block.addItemListener( new ItemListener() { @Override
		public void itemStateChanged(ItemEvent e) { m.update(); } });
		gpu.addItemListener( new ItemListener() { @Override
		public void itemStateChanged(ItemEvent e) { m.update(); } });
		it.addItemListener( new ItemListener() { @Override
		public void itemStateChanged(ItemEvent e) { m.update(); } });
		weight.addItemListener( new ItemListener() { @Override
		public void itemStateChanged(ItemEvent e) { m.update(); } });
		saveMem.addItemListener( new ItemListener() { @Override
		public void itemStateChanged(ItemEvent e) { m.update(); } });
	}

	@Override
	public void queryAdditionalParameters( final GenericDialog gd )
	{
		gd.addChoice( "ImgLib2_container_FFTs", BoundingBoxGUI.imgTypes, BoundingBoxGUI.imgTypes[ defaultFFTImgType ] );
		gd.addCheckbox( "Save_memory (not keep FFT's on CPU, 2x time & 0.5x memory)", defaultSaveMemory );
		saveMem = (Checkbox)gd.getCheckboxes().lastElement();
		gd.addChoice( "Type_of_iteration", iterationTypeString, iterationTypeString[ defaultIterationType ] );
		it = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "Image_weights", weightsString, weightsString[ defaultWeightType ] );
		weight = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "OSEM_acceleration", osemspeedupChoice, osemspeedupChoice[ defaultOSEMspeedupIndex ] );
		gd.addNumericField( "Number_of_iterations", defaultNumIterations, 0 );
		gd.addCheckbox( "Debug_mode", defaultDebugMode );
		gd.addCheckbox( "Adjust_blending_parameters (if stripes are visible)", defaultAdjustBlending );
		gd.addCheckbox( "Use_Tikhonov_regularization", defaultUseTikhonovRegularization );
		gd.addNumericField( "Tikhonov_parameter", defaultLambda, 4 );
		gd.addChoice( "Compute", blocksChoice, blocksChoice[ defaultBlockSizeIndex ] );
		block = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "Compute_on", computationOnChoice, computationOnChoice[ defaultComputationTypeIndex ] );
		gpu = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "PSF_estimation", extractPSFChoice, extractPSFChoice[ defaultExtractPSF ] );
		gd.addChoice( "PSF_display", displayPSFChoice, displayPSFChoice[ defaultDisplayPSF ] );
	}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd )
	{
		defaultFFTImgType = gd.getNextChoiceIndex();

		if ( defaultFFTImgType == 0 )
			computeFactory = new ArrayImgFactory< FloatType >();
		else if ( defaultFFTImgType == 1 )
			computeFactory = new ImagePlusImgFactory< FloatType >();
		else
			computeFactory = new CellImgFactory< FloatType >( 256 );

		saveMemory = defaultSaveMemory = gd.getNextBoolean();
		defaultIterationType = gd.getNextChoiceIndex();

		if ( defaultIterationType == 0 )
			iterationType = PSFTYPE.OPTIMIZATION_II;
		else if ( defaultIterationType == 1 )
			iterationType = PSFTYPE.OPTIMIZATION_I;
		else if ( defaultIterationType == 2 )
			iterationType = PSFTYPE.EFFICIENT_BAYESIAN;
		else //if ( defaultIterationType == 3 )
			iterationType = PSFTYPE.INDEPENDENT;

		defaultWeightType = gd.getNextChoiceIndex();

		if ( defaultWeightType == 0 )
			weightType = WeightType.PRECOMPUTED_WEIGHTS;
		else if ( defaultWeightType == 1 )
			weightType = WeightType.VIRTUAL_WEIGHTS;
		else if ( defaultWeightType == 2 )
			weightType = WeightType.NO_WEIGHTS;
		else
			weightType = WeightType.WEIGHTS_ONLY;

		osemspeedupIndex = defaultOSEMspeedupIndex = gd.getNextChoiceIndex();
		numIterations = defaultNumIterations = (int)Math.round( gd.getNextNumber() );
		debugMode = defaultDebugMode = gd.getNextBoolean();
		adjustBlending = defaultAdjustBlending = gd.getNextBoolean();
		useTikhonovRegularization = defaultUseTikhonovRegularization = gd.getNextBoolean();
		lambda = defaultLambda = gd.getNextNumber();
		blockSizeIndex = defaultBlockSizeIndex = gd.getNextChoiceIndex();
		computationTypeIndex = defaultComputationTypeIndex = gd.getNextChoiceIndex();
		extractPSFIndex = defaultExtractPSF = gd.getNextChoiceIndex();
		displayPSF = defaultDisplayPSF = gd.getNextChoiceIndex();

		return true;
	}

	@Override
	public Fusion newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new EfficientBayesianBased( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription() { return "Multi-view deconvolution"; }
	
	@Override
	public boolean supports16BitUnsigned() { return false; }

	@Override
	public boolean supportsDownsampling() { return false; }

	@Override
	public boolean compressBoundingBoxDialog() { return true; }

	@Override
	public long totalRAM( final long fusedSizeMB, final int bytePerPixel )
	{
		if ( weight.getSelectedIndex() == weightsString.length - 1 ) // only illustrate weights
			return fusedSizeMB * getMaxNumViewsPerTimepoint() + (avgPixels/ ( 1024*1024 )) * bytePerPixel;
		
		final int blockChoice = block.getSelectedIndex();
		
		final long blockSize;
		
		if ( blockChoice == 1 )
			blockSize = (64 * 64 * 64 * bytePerPixel)/(1024*1024);
		else if ( blockChoice == 2 )
			blockSize = (128 * 128 * 128 * bytePerPixel)/(1024*1024);
		else if ( blockChoice == 3 )
			blockSize = (256 * 256 * 256 * bytePerPixel)/(1024*1024);
		else if ( blockChoice == 4 )
			blockSize = (512 * 512 * 512 * bytePerPixel)/(1024*1024);
		else
			blockSize = fusedSizeMB;
		
		// transformed weight images + input data
		long totalRam;

		if ( weight.getSelectedIndex() == 0 ) // Precompute weights for all views (more memory, faster)
			totalRam = fusedSizeMB * ( getMaxNumViewsPerTimepoint() * 2 );
		else if ( weight.getSelectedIndex() == 1 ) // Virtual weights (less memory, slower)
			totalRam = fusedSizeMB * ( getMaxNumViewsPerTimepoint() + 1 );
		else // No weights (produces artifacts on partially overlapping data)
			totalRam = fusedSizeMB * ( getMaxNumViewsPerTimepoint() );

		// fft of psf's
		if ( gpu.getSelectedIndex() == 0 )
		{
			if ( saveMem.getState() == true )
				totalRam += blockSize * 1.5; // cpu, do not keep PSF FFTs
			else
				totalRam += blockSize * getMaxNumViewsPerTimepoint() * 1.5; // cpu, keep PSF FFTs
		}
		else
		{
			totalRam += (40 * 40 * 100 * bytePerPixel)/(1024*1024) * getMaxNumViewsPerTimepoint(); // gpu (40x40x100 approx PSF size)
		}

		// memory estimate for computing fft convolutions for images in RAM
		if ( gpu.getSelectedIndex() == 0 )
			totalRam += blockSize * 6 * 1.5;
		else
			totalRam += blockSize * 2;

		// the output + 2xtmp
		totalRam += fusedSizeMB * 3;

		return totalRam;
	}

	protected void displayParametersAndPSFs( final BoundingBoxGUI bb, final Channel channel, final HashMap< Channel, ChannelPSF > extractPSFLabels  )
	{
		IOFunctions.println( "Type of iteration: " + iterationType );
		IOFunctions.println( "Number iterations: " + numIterations );
		IOFunctions.println( "OSEM speedup: " + osemSpeedUp );
		IOFunctions.println( "Using blocks: " + useBlocks );
		if ( useBlocks )
			IOFunctions.println( "Block size: " + Util.printCoordinates( blockSize ) );
		IOFunctions.println( "Using CUDA: " + useCUDA );

		IOFunctions.println( "Blending border: " + blendingBorderX + "x" + blendingBorderY + "x" + blendingBorderZ );
		IOFunctions.println( "Blending range: " + blendingRangeX + "x" + blendingRangeY + "x" + blendingRangeZ );

		if ( extractPSF )
		{
			IOFunctions.println( "PSF size (extracting): " + psfSizeX + "x" + psfSizeY + "x" + psfSizeZ );
			
			for ( final ChannelPSF c : extractPSFLabels.values() )
			{
				if ( c.getOtherChannel() == null )
					IOFunctions.println( "Channel " + c.getChannel().getName() + " extracts from label '" + c.getLabel() + "'. " );
				else
					IOFunctions.println( "Channel " + c.getChannel().getName() + " uses same PSF as channel '" + c.getOtherChannel().getName() + "'. " );
			}
		}
		else
		{
			int size = 0;
			for ( final Channel c : psfFiles.keySet() )
				size += psfFiles.get( c ).size();

			IOFunctions.println( "PSF will be read from disc, number of PSF's loaded: " + size );
		}
		
		if ( debugMode )
			IOFunctions.println( "Debugging every " + debugInterval + " iterations." );
	
		IOFunctions.println( "ImgLib container (deconvolved): " + bb.getImgFactory( new FloatType() ).getClass().getSimpleName() );
		
		if ( useTikhonovRegularization )
			IOFunctions.println( "Using Tikhonov regularization (lambda = " + lambda + ")" );
		else
			IOFunctions.println( "Not using Tikhonov regularization" );

		// "Do not show PSFs", 
		// "Show MIP of combined PSF's",
		// "Show combined PSF's",
		// "Show individual PSF's",
		// "Show combined PSF's (original scale)",
		// "Show individual PSF's (original scale)" };
		
		final ExtractPSF< FloatType > ePSF = extractPSFLabels.get( channel ).getExtractPSFInstance(); 
		final DisplayImage di = new DisplayImage();

		if ( displayPSF == 1 )
		{
			di.exportImage( ExtractPSF.computeMaxProjection( ePSF.computeAverageTransformedPSF(), -1 ), "Max projected avg transformed PSF's" );
		}
		else if ( displayPSF == 2 )
		{
			di.exportImage( ePSF.computeAverageTransformedPSF(), "Avg transformed PSF's" );				
		}
		else if ( displayPSF == 3 )
		{
			for ( int i = 0; i < ePSF.getPSFMap().values().size(); ++i )
			{
				final ViewId viewId = ePSF.getViewIdsForPSFs().get( i );
				di.exportImage( ePSF.getTransformedPSF( viewId ), "transfomed PSF of viewsetup " + viewId.getViewSetupId() );
			}
		}
		else if ( displayPSF == 4 )
		{
			di.exportImage( ePSF.computeAveragePSF(), "Avg original PSF's" );				
		}
		else if ( displayPSF == 5 )
		{
			for ( int i = 0; i < ePSF.getInputCalibrationPSFs().size(); ++i )
			{
				final ViewId viewId = ePSF.getViewIdsForPSFs().get( i );
				di.exportImage( ePSF.getInputCalibrationPSFs().get( viewId ), "original PSF of viewsetup " + viewId.getViewSetupId() );
			}
		}
	}

	/**
	 * Order the channels in a way so that those were the beads are extracted from, are first.
	 * Otherwise, the extracted PSF will not be avaiable for a certain channel that uses PSFs
	 * from another channel
	 * 
	 * @return
	 */
	protected boolean reOrderChannels()
	{
		final ArrayList< Channel > channelsToExtract = new ArrayList< Channel >();
		final ArrayList< Channel > channelsUsingAnotherPSF = new ArrayList< Channel >();
		
		for ( final Channel c : channelsToProcess )
		{
			if ( extractPSFLabels.get( c ).getLabel() == null )
				channelsUsingAnotherPSF.add( c );
			else
				channelsToExtract.add( c );
		}
		
		// check that there is at least one channel that extracts
		if ( channelsToExtract.size() == 0 )
		{
			IOFunctions.println( "At least one channel needs to extract PSFs. Stopping." );
			return false;
		}
		
		// test that each channel using the PSF from another channel actually links to one that extracts
		for ( final Channel c : channelsUsingAnotherPSF )
		{
			if ( extractPSFLabels.get( extractPSFLabels.get( c ).getOtherChannel() ).getLabel() == null )
			{
				IOFunctions.println( "Channel " + c.getName() + " is supposed to use the PSF from channel " +
								extractPSFLabels.get( c ).getOtherChannel().getName() + ", but this one also does not" +
								"extract PSFs. Stopping." );
				return false;
			}
		}
		
		this.channelsToProcess.clear();
		
		this.channelsToProcess.addAll( channelsToExtract );
		this.channelsToProcess.addAll( channelsUsingAnotherPSF );
		
		return true;
	}

	protected boolean getBlending()
	{
		if ( adjustBlending )
		{
			final GenericDialog gd = new GenericDialog( "Adjust blending parameters" );
			
			if ( defaultBlendingBorder == null || defaultBlendingBorder.length < 3 )
				defaultBlendingBorder = new int[]{ defaultBlendingBorderNumber, defaultBlendingBorderNumber, Math.round( defaultBlendingBorderNumber/2.5f ) };
			
			if ( defaultBlendingRange == null || defaultBlendingRange.length < 3 )
				defaultBlendingRange =  new int[]{ defaultBlendingRangeNumber, defaultBlendingRangeNumber, defaultBlendingRangeNumber };
			
			gd.addSlider( "Boundary_pixels_X", -50, 50, defaultBlendingBorder[ 0 ] );
			gd.addSlider( "Boundary_pixels_Y", -50, 50, defaultBlendingBorder[ 1 ] );
			gd.addSlider( "Boundary_pixels_Z", -50, 50, defaultBlendingBorder[ 2 ] );
			gd.addSlider( "Blending_range_X", 0, 100, defaultBlendingRange[ 0 ] );
			gd.addSlider( "Blending_range_Y", 0, 100, defaultBlendingRange[ 1 ] );
			gd.addSlider( "Blending_range_Z", 0, 100, defaultBlendingRange[ 2 ] );
			
			gd.addMessage( "" );
			gd.addMessage( "Note: both sizes are in local coordinates of the input views. Increase one or both of those values if stripy artifacts\n" +
						   "are visible in the deconvolution result.\n" +
						   "The boundary pixels describe a range of pixels at the edge of each input view that are discarded because of the PSF size,\n" +
						   "it should typically correspond to half the size of the extracted PSF.\n" +
						   "The blending range defines in which outer part of each view the cosine blending is performed. You can manually inspect\n" +
						   "the results of these operations by choosing 'Illustrate overlap of views per pixel (do not deconvolve)' in the previous\n" +
						   "dialog. Choose just one input view to get an idea of what is cut off for individual stacks.", GUIHelper.mediumstatusfont );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return false;
			
			blendingBorderX = defaultBlendingBorder[ 0 ] = (int)Math.round( gd.getNextNumber() );
			blendingBorderY = defaultBlendingBorder[ 1 ] = (int)Math.round( gd.getNextNumber() );
			blendingBorderZ = defaultBlendingBorder[ 2 ] = (int)Math.round( gd.getNextNumber() );
			blendingRangeX = defaultBlendingRange[ 0 ] = (int)Math.round( gd.getNextNumber() );
			blendingRangeY = defaultBlendingRange[ 1 ] = (int)Math.round( gd.getNextNumber() );
			blendingRangeZ = defaultBlendingRange[ 2 ] = (int)Math.round( gd.getNextNumber() );
		}
		else
		{
			if ( defaultBlendingBorder != null && defaultBlendingBorder.length >= 3 )
			{
				blendingBorderX = defaultBlendingBorder[ 0 ];
				blendingBorderY = defaultBlendingBorder[ 1 ];
				blendingBorderZ = defaultBlendingBorder[ 2 ];
			}
			else
			{
				blendingBorderX = defaultBlendingBorderNumber;
				blendingBorderY = defaultBlendingBorderNumber;
				blendingBorderZ = Math.round( defaultBlendingBorderNumber/2.5f );
			}
			
			if ( defaultBlendingRange != null && defaultBlendingRange.length >= 3 )
			{
				blendingRangeX = defaultBlendingRange[ 0 ];
				blendingRangeY = defaultBlendingRange[ 1 ];
				blendingRangeZ = defaultBlendingRange[ 2 ];
			}
			else
			{
				blendingRangeX = defaultBlendingRangeNumber;
				blendingRangeY = defaultBlendingRangeNumber;
				blendingRangeZ = defaultBlendingRangeNumber;
			}
		}
		
		return true;
	}

	protected boolean getPSF()
	{
		if ( extractPSFIndex == 0 )
		{
			extractPSF = true;
			this.psfFiles = null;

			final HashMap< Channel, ArrayList< Correspondence > > correspondences = new HashMap< Channel, ArrayList< Correspondence > >();

			// get all interest point labels that have correspondences for all views that are processed
			assembleAvailableCorrespondences( correspondences, new HashMap< Channel, Integer >(), true );

			int sumChannels = 0;
			for ( final Channel c : correspondences.keySet() )
				sumChannels += correspondences.get( c ).size();

			if ( sumChannels == 0 )
			{
				IOFunctions.println( "No detections that have been registered are available to extract a PSF. Quitting." );
				return false;
			}

			// make a list of those labels for the imagej dialog
			// and set the default selections
			final String[][] choices = new String[ channelsToProcess.size() ][];

			if ( defaultPSFLabelIndex == null || defaultPSFLabelIndex.length != channelsToProcess.size() )
				defaultPSFLabelIndex = new int[ channelsToProcess.size() ];

			// remember which choiceindex in the dialog maps to which other channel
			final ArrayList< HashMap< Integer, Channel > > otherChannels = new ArrayList< HashMap< Integer, Channel > >();
			
			for ( int i = 0; i < channelsToProcess.size(); ++i )
			{
				final Channel c = channelsToProcess.get( i );
				final ArrayList< Correspondence > corr = correspondences.get( c ); 
				choices[ i ] = new String[ corr.size() + channelsToProcess.size() - 1 ];
				
				for ( int j = 0; j < corr.size(); ++j )
					choices[ i ][ j ] = corr.get( j ).getLabel();
				
				final HashMap< Integer, Channel > otherChannel = new HashMap< Integer, Channel >();
				
				int k = 0;
				for ( int j = 0; j < channelsToProcess.size(); ++j )
				{
					if ( !channelsToProcess.get( j ).equals( c ) )
					{
						choices[ i ][ k + corr.size() ] = "Same PSF as channel " + channelsToProcess.get( j ).getName();
						otherChannel.put( k + corr.size(), channelsToProcess.get( j ) );
						++k;
					}
				}
				
				otherChannels.add( otherChannel );
				
				if ( defaultPSFLabelIndex[ i ] < 0 || defaultPSFLabelIndex[ i ] >= choices[ i ].length )
					defaultPSFLabelIndex[ i ] = 0;
			}
			
			final GenericDialogPlus gd = new GenericDialogPlus( "Extract PSF's ..." );
			
			for ( int j = 0; j < channelsToProcess.size(); ++j )
				gd.addChoice( "Detections_to_extract_PSF_for_channel_" + channelsToProcess.get( j ).getName(), choices[ j ], choices[ j ][ defaultPSFLabelIndex[ j ] ] );

			gd.addMessage( "" );

			gd.addSlider( "PSF_size_X", 9, 100, defaultPSFSizeX );
			gd.addSlider( "PSF_size_Y", 9, 100, defaultPSFSizeY );
			gd.addSlider( "PSF_size_Z", 9, 100, defaultPSFSizeZ );
			
			gd.addMessage( " \nNote: PSF size is in local coordinates [px] of the input view.", GUIHelper.mediumstatusfont );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return false;
			
			this.extractPSFLabels = new HashMap< Channel, ChannelPSF >();
			
			for ( int j = 0; j < channelsToProcess.size(); ++j )
			{
				final Channel c = channelsToProcess.get( j );
				final int l = defaultPSFLabelIndex[ j ] = gd.getNextChoiceIndex();
				
				if ( l < correspondences.get( c ).size() )
				{
					this.extractPSFLabels.put( c, new ChannelPSF( c, choices[ j ][ l ] ) );
					IOFunctions.println( "Channel " + c.getName() + ": extract PSF from label '" + choices[ j ][ l ] + "'" );
				}
				else
				{
					this.extractPSFLabels.put( c, new ChannelPSF( c, otherChannels.get( j ).get( l ) ) );
					IOFunctions.println( "Channel " + c.getName() + ": uses same PSF as channel " + this.extractPSFLabels.get( c ).getOtherChannel().getName() );
				}
			}

			psfSizeX = defaultPSFSizeX = (int)Math.round( gd.getNextNumber() );
			psfSizeY = defaultPSFSizeY = (int)Math.round( gd.getNextNumber() );
			psfSizeZ = defaultPSFSizeZ = (int)Math.round( gd.getNextNumber() );

			// enforce odd number
			if ( psfSizeX % 2 == 0 )
				defaultPSFSizeX = ++psfSizeX;

			if ( psfSizeY % 2 == 0 )
				defaultPSFSizeY = ++psfSizeY;

			if ( psfSizeZ % 2 == 0 )
				defaultPSFSizeZ = ++psfSizeZ;
		}
		else
		{
			extractPSF = false;
			this.extractPSFLabels = new HashMap< Channel, ChannelPSF >();
			for ( final Channel c : channelsToProcess )
				this.extractPSFLabels.put( c, new ChannelPSF( c ) );

			final List< Angle > anglesToProcess = SpimData2.getAllAnglesSorted( spimData, viewIdsToProcess );
			final List< Illumination > illumsToProcess = SpimData2.getAllIlluminationsSorted( spimData, viewIdsToProcess );

			if ( anglesToProcess.size() * illumsToProcess.size() * channelsToProcess.size() > 1 )
			{
				final GenericDialogPlus gd = new GenericDialogPlus( "Load PSF File ..." );
	
				if ( anglesToProcess.size() * illumsToProcess.size() > 1 )
					gd.addCheckbox( "Use_same_PSF_for_all_angles/illuminations", defaultSamePSFForAllAnglesIllums );
	
				if ( channelsToProcess.size() > 1 )
					gd.addCheckbox( "Use_same_PSF_for_all_channels", defaultSamePSFForAllChannels );
				
				gd.showDialog();
	
				if ( gd.wasCanceled() )
					return false;
				
				if ( anglesToProcess.size() * illumsToProcess.size() > 1 )
					defaultSamePSFForAllAnglesIllums = gd.getNextBoolean();
				if ( channelsToProcess.size() > 1 )
					defaultSamePSFForAllChannels = gd.getNextBoolean();
			}
			else
			{
				defaultSamePSFForAllAnglesIllums = defaultSamePSFForAllChannels = true;
			}

			final GenericDialogPlus gd2 = new GenericDialogPlus( "Select PSF File ..." );

			gd2.addCheckbox( "Transform_PSFs", defaultTransformPSFs );
			gd2.addMessage( "" );
			gd2.addMessage( "Note: the calibration of the PSF(s) has to match the calibration of the input views\n" +
					"if you choose to transform them according to the registration of the views!", GUIHelper.mediumstatusfont );

			int numPSFs;

			if ( defaultSamePSFForAllAnglesIllums )
				numPSFs = 1;
			else // TODO: ignores potentially missing or not existing views 
				numPSFs = anglesToProcess.size() * illumsToProcess.size();

			if ( !defaultSamePSFForAllChannels )
				numPSFs *= channelsToProcess.size();

			if ( defaultPSFFileField == null )
				defaultPSFFileField = new ArrayList<String>();

			while( defaultPSFFileField.size() < numPSFs )
				defaultPSFFileField.add( "" );

			if ( defaultPSFFileField.size() > numPSFs )
				for ( int i = numPSFs; i < defaultPSFFileField.size(); ++i )
					defaultPSFFileField.remove( numPSFs );

			if ( defaultSamePSFForAllAnglesIllums )
			{
				if ( defaultSamePSFForAllChannels )
				{
					gd2.addFileField( "PSF_file", defaultPSFFileField.get( 0 ), 50 );
				}
				else
				{
					int j = 0;
					
					for ( final Channel c : channelsToProcess )
						gd2.addFileField( "PSF_file_(channel=" + c.getName() + ")", defaultPSFFileField.get( j++ ), 50 );
				}
			}
			else
			{
				int j = 0;
				
				if ( defaultSamePSFForAllChannels )
				{
					for ( final Illumination i : illumsToProcess )
						for ( final Angle a : anglesToProcess )
							gd2.addFileField( "PSF_file_(angle=" + a.getName() + ", illum=" + i.getName() + ")", defaultPSFFileField.get( j++ ), 50 );
				}
				else
				{
					for ( final Channel c : channelsToProcess )
						for ( final Illumination i : illumsToProcess )
							for ( final Angle a : anglesToProcess )
								gd2.addFileField( "PSF_file_(angle=" + a.getName() + ", illum=" + i.getName() + ", channel=" + c.getName() + ")", defaultPSFFileField.get( j++ ), 50 );
				}
			}
			
			GUIHelper.addScrollBars( gd2 );
			
			gd2.showDialog();
			
			if ( gd2.wasCanceled() )
				return false;

			transformPSFs = defaultTransformPSFs = gd2.getNextBoolean();
			
			defaultPSFFileField.clear();
			
			for ( int i = 0; i < numPSFs; ++i )
				defaultPSFFileField.add( gd2.getNextString() );
				
			psfFiles = new HashMap< Channel, ArrayList< Pair< Pair< Angle,Illumination >, String > > >();
			
			if ( defaultSamePSFForAllAnglesIllums )
			{
				if ( defaultSamePSFForAllChannels )
				{
					// as many times the same filename as there are illuminations and angles
					final String fileName = defaultPSFFileField.get( 0 );

					final ArrayList< Pair< Pair< Angle,Illumination >, String > > files = new ArrayList< Pair< Pair< Angle,Illumination >, String > >();
					for( final Illumination i : illumsToProcess )
						for ( final Angle a : anglesToProcess )
						{
							final Pair< Angle, Illumination > aiPair = new ValuePair< Angle, Illumination >( a, i );
							files.add( new ValuePair< Pair< Angle,Illumination >, String >( aiPair, fileName ) );
						}

					for ( final Channel c : channelsToProcess )
						psfFiles.put( c, files );
				}
				else
				{
					int j = 0;
					
					for ( final Channel c : channelsToProcess )
					{
						final ArrayList< Pair< Pair< Angle,Illumination >, String > > files = new ArrayList< Pair< Pair< Angle,Illumination >, String > >();

						// as many times the same filename as there are illuminations and angles
						final String fileName = defaultPSFFileField.get( j );

						for( final Illumination i : illumsToProcess )
							for ( final Angle a : anglesToProcess )
							{
								final Pair< Angle, Illumination > aiPair = new ValuePair< Angle, Illumination >( a, i );
								files.add( new ValuePair< Pair< Angle,Illumination >, String >( aiPair, fileName ) );
							}

						psfFiles.put( c, files );
						++j;
					}
				}
			}
			else
			{
				if ( defaultSamePSFForAllChannels )
				{
					final ArrayList< Pair< Pair< Angle,Illumination >, String > > files = new ArrayList< Pair< Pair< Angle,Illumination >, String > >();

					// one filename per angle/illum pair
					int j = 0;

					for( final Illumination i : illumsToProcess )
						for ( final Angle a : anglesToProcess )
						{
							final Pair< Angle, Illumination > aiPair = new ValuePair< Angle, Illumination >( a, i );
							files.add( new ValuePair< Pair< Angle,Illumination >, String >( aiPair, defaultPSFFileField.get( j++ ) ) );
						}

					for ( final Channel c : channelsToProcess )
						psfFiles.put( c, files );
				}
				else
				{
					// one filename per angle/illum pair and channel
					int j = 0;
					
					for ( final Channel c : channelsToProcess )
					{
						final ArrayList< Pair< Pair< Angle,Illumination >, String > > files = new ArrayList< Pair< Pair< Angle,Illumination >, String > >();

						for( final Illumination i : illumsToProcess )
							for ( final Angle a : anglesToProcess )
							{
								final Pair< Angle, Illumination > aiPair = new ValuePair< Angle, Illumination >( a, i );
								files.add( new ValuePair< Pair< Angle,Illumination >, String >( aiPair, defaultPSFFileField.get( j++ ) ) );
							}

						psfFiles.put( c, files );
					}
				}
			}	
		}
		
		return true;
	}
	
	protected boolean getOSEM()
	{
		if ( osemspeedupIndex == 0 )
		{
			defaultOSEMspeedup = osemSpeedUp = 1;
		}
		else if ( osemspeedupIndex == 3 )
		{
			GenericDialog gdOSEM = new GenericDialog( "OSEM options" );
			gdOSEM.addNumericField( "Additional_acceleration = ", defaultOSEMspeedup, 2 );
			gdOSEM.showDialog();
			
			if ( gdOSEM.wasCanceled() )
				return false;
			
			defaultOSEMspeedup = osemSpeedUp = gdOSEM.getNextNumber();			
		}
		
		return true;
	}
	
	protected boolean getDebug()
	{
		if ( weightType == WeightType.WEIGHTS_ONLY )
			return true;

		if ( debugMode )
		{
			GenericDialog gdDebug = new GenericDialog( "Debug options" );
			gdDebug.addNumericField( "Show debug output every n'th frame, n = ", defaultDebugInterval, 0 );
			gdDebug.showDialog();
			
			if ( gdDebug.wasCanceled() )
				return false;
			
			defaultDebugInterval = debugInterval = (int)Math.round( gdDebug.getNextNumber() );
		}
		
		return true;
	}
	
	protected boolean getBlocks()
	{
		if ( blockSizeIndex == 0 )
		{
			this.useBlocks = false;
			this.blockSize = null;
		}
		else if ( blockSizeIndex == 1 )
		{
			this.useBlocks = true;
			this.blockSize = new int[]{ 64, 64, 64 };
		}
		else if ( blockSizeIndex == 2 )
		{
			this.useBlocks = true;
			this.blockSize = new int[]{ 128, 128, 128 };
		}
		else if ( blockSizeIndex == 3 )
		{
			this.useBlocks = true;
			this.blockSize = new int[]{ 256, 256, 256 };
		}
		else if ( blockSizeIndex == 4 )
		{
			this.useBlocks = true;
			blockSize = new int[]{ 512, 512, 512 };
		}
		if ( blockSizeIndex == 5 )
		{
			GenericDialog gd = new GenericDialog( "Define block sizes" );
			
			gd.addNumericField( "blocksize_x", defaultBlockSizeX, 0 );
			gd.addNumericField( "blocksize_y", defaultBlockSizeY, 0 );
			gd.addNumericField( "blocksize_z", defaultBlockSizeZ, 0 );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return false;
			
			defaultBlockSizeX = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );
			defaultBlockSizeY = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );
			defaultBlockSizeZ = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );

			this.useBlocks = true;
			this.blockSize = new int[]{ defaultBlockSizeX, defaultBlockSizeY, defaultBlockSizeZ };
		}

		return true;
	}
	
	protected boolean getCUDA()
	{
		// we need to popluate the deviceList in any case
		deviceList = new ArrayList< CUDADevice >();
		
		if ( computationTypeIndex == 0 )
		{
			deviceList.add( new CUDADevice( -1, "CPU", Runtime.getRuntime().maxMemory(), Runtime.getRuntime().freeMemory(), 0, 0 ) );
			useCUDA = false;
		}
		else
		{
			final ArrayList< String > potentialNames = new ArrayList< String >();
			potentialNames.add( "fftCUDA" );
			potentialNames.add( "FourierConvolutionCUDA" );
			
			MVDeconFFT.cuda = NativeLibraryTools.loadNativeLibrary( potentialNames, CUDAFourierConvolution.class );

			if ( MVDeconFFT.cuda == null )
			{
				IOFunctions.println( "Cannot load CUDA JNA library." );
				return false;
			}
			
			final ArrayList< CUDADevice > selectedDevices = CUDATools.queryCUDADetails( MVDeconFFT.cuda, useBlocks );
			
			if ( selectedDevices == null || selectedDevices.size() == 0 )
				return false;
			else
				deviceList.addAll( selectedDevices );

			// yes, CUDA is possible
			useCUDA = true;
		}
		
		return true;
	}
	/**
	 * 
	 * @param correspondences
	 * @param viewsPresent
	 * @param onlyValid - only return a list of correspondence labels if all views have correspondences
	 */
	protected void assembleAvailableCorrespondences( final HashMap< Channel, ArrayList< Correspondence > > correspondences, final HashMap< Channel, Integer > viewsPresent, final boolean onlyValid )
	{
		final ViewInterestPoints vp = spimData.getViewInterestPoints();
				
		for ( final Channel c : channelsToProcess )
		{
			int countViews = 0;
			
			final ArrayList< Correspondence > corrList = new ArrayList< Correspondence >();
			
			for ( final TimePoint t : timepointsToProcess )
				for ( final ViewId viewId : SpimData2.getAllViewIdsForTimePointSorted( spimData, viewIdsToProcess, t ) )
				{
					final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId ); 
					
					if ( vd.getViewSetup().getChannel().getId() == c.getId() && vd.isPresent() )
					{
						// how many views with correspondences should be there
						++countViews;

						// the object with links to all available detections
						final ViewInterestPointLists vpl = vp.getViewInterestPointLists( viewId );
						
						// the list of all available detections
						for ( final String label : vpl.getHashMap().keySet() )
						{
							final InterestPointList ipl = vpl.getInterestPointList( label );

							final String name =
									label + " --- channel: " + c.getName() + " angle: " + vd.getViewSetup().getAngle().getName() +
									" illum: " + vd.getViewSetup().getIllumination().getName() + " timepoint: " + t.getName() + ": ";

							if ( ipl.getInterestPoints() == null )
								ipl.loadInterestPoints();
							
							if ( ipl.getCorrespondingInterestPoints() == null )
								ipl.loadCorrespondingInterestPoints();
							
							if ( ipl.getCorrespondingInterestPoints().size() > 0 )
							{
								Correspondence corrTmp = new Correspondence( label );
								boolean foundEntry = false;
								
								for ( final Correspondence corr : corrList )
								{
									if ( corr.equals( corrTmp ) )
									{
										corr.increaseCount();
										foundEntry = true;
										break;
									}
								}
								
								if ( !foundEntry )
									corrList.add( corrTmp );
								
								IOFunctions.println( name + ipl.getCorrespondingInterestPoints().size() + " correspondences." );
							}
							else
							{
								IOFunctions.println( name + " NO correspondences." );
							}
						}
					}
				}
			
			correspondences.put( c, corrList );
			viewsPresent.put( c, countViews );
		}
		
		for ( final Channel c : channelsToProcess )
		{
			IOFunctions.println();
			IOFunctions.println( "Found " + correspondences.get( c ).size() + " label(s) with correspondences for channel " + c.getName() + ": " );
			
			final ArrayList< Correspondence > newList = new ArrayList< Correspondence >();
			
			for ( final Correspondence corr : correspondences.get( c ) )
			{
				final int numViews = viewsPresent.get( c );
				IOFunctions.println( "Label '" + corr.getLabel() + "' (channel " + c.getName() + ") has " + corr.getCount() + "/" + numViews + " views with corresponding detections." );
				
				if ( !onlyValid || corr.getCount() == numViews )
					newList.add( corr );
			}
			
			correspondences.remove( c );
			correspondences.put( c, newList );
		}

	}
	
	protected class Correspondence
	{
		final String label;
		int count;
		
		public Correspondence( final String label )
		{
			this.label = label;
			this.count = 1;
		}
		
		public void increaseCount() { ++count; }
		public int getCount() { return count; }
		public String getLabel() { return label; }
		
		@Override
		public boolean equals( final Object o )
		{
			if ( o instanceof Correspondence )
				return ( (Correspondence)o ).getLabel().equals( this.getLabel() );
			else
				return false;
		}
	}

	@Override
	protected Map< ViewSetup, ViewSetup > createNewViewSetups( final BoundingBoxGUI bb )
	{
		return WeightedAverageFusion.assembleNewViewSetupsFusion( spimData, viewIdsToProcess, bb, "Decon", "Decon" );
	}
}
