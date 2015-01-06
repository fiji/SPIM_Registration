package spim.process.fusion.deconvolution;

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;

import java.awt.Choice;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
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
import mpicbg.spim.postprocessing.deconvolution2.BayesMVDeconvolution;
import mpicbg.spim.postprocessing.deconvolution2.LRFFT;
import mpicbg.spim.postprocessing.deconvolution2.LRFFT.PSFTYPE;
import mpicbg.spim.postprocessing.deconvolution2.LRInput;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.fusion.BoundingBox;
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
import spim.process.fusion.boundingbox.ManualBoundingBox.ManageListeners;
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
		"Independent (slow, very precise)",
		"Illustrate overlap of views per pixel (do not deconvolve)" };

	public static boolean makeAllPSFSameSize = false;
	
	public static int defaultIterationType = 1;
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
	public static boolean defaultSamePSFForAllViews = true;
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
	boolean justShowWeights;
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

	boolean useBlocks;
	int[] blockSize;
	boolean useCUDA;
	int debugInterval;
	double osemSpeedUp;
	boolean extractPSF;
	boolean transformPSFs;
	HashMap< Channel, ArrayList< String > > psfFiles;
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

	Choice gpu, block, it;

	public EfficientBayesianBased(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
		
		// we want the arrayimg by default
		BoundingBox.defaultImgType = 0;
		
		// linear interpolation
		Fusion.defaultInterpolation = this.interpolation = 1;
	}

	@Override
	public boolean fuseData( final BoundingBox bb, final ImgExport exporter )
	{
		// set up naming scheme
		final FixedNameImgTitler titler = new FixedNameImgTitler( "" );
		if ( exporter instanceof ImgExportTitle )
			( (ImgExportTitle)exporter).setImgTitler( titler );

		final ProcessForDeconvolution pfd = new ProcessForDeconvolution(
				spimData,
				anglesToProcess,
				illumsToProcess, 
				bb, 
				new int[]{ blendingBorderX, blendingBorderY, blendingBorderZ },
				new int[]{ blendingRangeX, blendingRangeY, blendingRangeZ } );
		
		// set debug mode
		BayesMVDeconvolution.debug = debugMode;
		BayesMVDeconvolution.debugInterval = debugInterval;

		String illumName = "_Ill" + illumsToProcess.get( 0 ).getName();

		for ( int i = 1; i < illumsToProcess.size(); ++i )
			illumName += "," + illumsToProcess.get( i ).getName();

		String angleName = "_Ang" + anglesToProcess.get( 0 ).getName();

		for ( int i = 1; i < anglesToProcess.size(); ++i )
			angleName += "," + anglesToProcess.get( i ).getName();

		int stack = 0;

		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
			{
				// fuse the images, create weights, extract PSFs we need for the deconvolution
				if ( !pfd.fuseStacksAndGetPSFs(
						t, c,
						osemspeedupIndex,
						osemSpeedUp,
						justShowWeights,
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

				if ( justShowWeights )
					return true;
				
				final LRInput deconvolutionData = new LRInput();

				for ( int view = 0; view < anglesToProcess.size() * illumsToProcess.size(); ++view )
				{
					// device list for CPU or CUDA processing
					final int[] devList = new int[ deviceList.size() ];
					for ( int i = 0; i < devList.length; ++i )
						devList[ i ] = deviceList.get( i ).getDeviceId();
					
					deconvolutionData.add( new LRFFT( 
							pfd.getTransformedImgs().get( view ),
							pfd.getTransformedWeights().get( view ),
							pfd.getExtractPSF().getTransformedPSFs().get( view ), devList, useBlocks, blockSize ) );
				}

				final Img<FloatType> deconvolved;
				
				if ( useTikhonovRegularization )
					deconvolved = LRFFT.wrap( new BayesMVDeconvolution( deconvolutionData, iterationType, numIterations, lambda, osemSpeedUp, osemspeedupIndex, "deconvolved" ).getPsi() );
				else
					deconvolved = LRFFT.wrap( new BayesMVDeconvolution( deconvolutionData, iterationType, numIterations, 0, osemSpeedUp, osemspeedupIndex, "deconvolved" ).getPsi() );

				// export the final image
				titler.setTitle( "TP" + t.getName() + "_Ch" + c.getName() + illumName + angleName );
				exporter.exportImage(
						deconvolved,
						bb,
						t,
						newViewsetups.get( SpimData2.getViewSetup( spimData.getSequenceDescription().getViewSetupsOrdered(), c, anglesToProcess.get( 0 ), illumsToProcess.get( 0 ) ) ),
						0, 1 );
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
	}

	@Override
	public void queryAdditionalParameters( final GenericDialog gd )
	{
		gd.addChoice( "Type_of_iteration", iterationTypeString, iterationTypeString[ defaultIterationType ] );
		it = (Choice)gd.getChoices().lastElement();
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
		defaultIterationType = gd.getNextChoiceIndex();
		
		justShowWeights = false;
		
		if ( defaultIterationType == 0 )
			iterationType = PSFTYPE.OPTIMIZATION_II;
		else if ( defaultIterationType == 1 )
			iterationType = PSFTYPE.OPTIMIZATION_I;
		else if ( defaultIterationType == 2 )
			iterationType = PSFTYPE.EFFICIENT_BAYESIAN;
		else if ( defaultIterationType == 3 )
			iterationType = PSFTYPE.INDEPENDENT;
		else
			justShowWeights = true; // just show the overlap
		
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
	public Fusion newInstance(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess)
	{
		return new EfficientBayesianBased( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
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
		if ( it.getSelectedIndex() == iterationTypeString.length - 1 )
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
		long totalRam = fusedSizeMB * getMaxNumViewsPerTimepoint() * 2;
		
		// fft of psf's
		if ( gpu.getSelectedIndex() == 0 )
			totalRam += blockSize * getMaxNumViewsPerTimepoint() * 1.5; // cpu
		else
			totalRam += (40 * 40 * 100 * bytePerPixel)/(1024*1024) * getMaxNumViewsPerTimepoint(); // gpu
		
		// memory estimate for computing fft convolutions for images in RAM
		if ( gpu.getSelectedIndex() == 0 )
			totalRam += blockSize * 6 * 1.5;
		else
			totalRam += blockSize * 2;
		
		// the output image
		totalRam += fusedSizeMB;
		
		return totalRam;
	}

	protected void displayParametersAndPSFs( final BoundingBox bb, final Channel channel, final HashMap< Channel, ChannelPSF > extractPSFLabels  )
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

		// only if the PSF was extracted in this channel
		if ( extractPSFLabels.get( channel ).isExtractedPSF() )
		{
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
				for ( int i = 0; i < ePSF.getTransformedPSFs().size(); ++i )
					di.exportImage( ePSF.getTransformedPSFs().get( i ), "transfomed PSF of viewsetup " + ePSF.getViewDescriptionsForPSFs().get( i ).getViewSetupId() );
			}
			else if ( displayPSF == 4 )
			{
				di.exportImage( ePSF.computeAveragePSF(), "Avg original PSF's" );				
			}
			else if ( displayPSF == 5 )
			{
				for ( int i = 0; i < ePSF.getInputCalibrationPSFs().size(); ++i )
					di.exportImage( ePSF.getInputCalibrationPSFs().get( i ), "original PSF of viewsetup " + ePSF.getViewDescriptionsForPSFs().get( i ).getViewSetupId() );				
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
			
			if ( ProcessForDeconvolution.defaultBlendingBorder == null || ProcessForDeconvolution.defaultBlendingBorder.length < 3 )
				ProcessForDeconvolution.defaultBlendingBorder = new int[]{ ProcessForDeconvolution.defaultBlendingBorderNumber, ProcessForDeconvolution.defaultBlendingBorderNumber, ProcessForDeconvolution.defaultBlendingBorderNumber };
			
			if ( ProcessForDeconvolution.defaultBlendingRange == null || ProcessForDeconvolution.defaultBlendingRange.length < 3 )
				ProcessForDeconvolution.defaultBlendingRange =  new int[]{ ProcessForDeconvolution.defaultBlendingRangeNumber, ProcessForDeconvolution.defaultBlendingRangeNumber, ProcessForDeconvolution.defaultBlendingRangeNumber };
			
			gd.addSlider( "Boundary_pixels_X", 0, 100, ProcessForDeconvolution.defaultBlendingBorder[ 0 ] );
			gd.addSlider( "Boundary_pixels_Y", 0, 100, ProcessForDeconvolution.defaultBlendingBorder[ 1 ] );
			gd.addSlider( "Boundary_pixels_Z", 0, 100, ProcessForDeconvolution.defaultBlendingBorder[ 2 ] );
			gd.addSlider( "Blending_range_X", 0, 100, ProcessForDeconvolution.defaultBlendingRange[ 0 ] );
			gd.addSlider( "Blending_range_Y", 0, 100, ProcessForDeconvolution.defaultBlendingRange[ 1 ] );
			gd.addSlider( "Blending_range_Z", 0, 100, ProcessForDeconvolution.defaultBlendingRange[ 2 ] );
			
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
			
			blendingBorderX = ProcessForDeconvolution.defaultBlendingBorder[ 0 ] = (int)Math.round( gd.getNextNumber() );
			blendingBorderY = ProcessForDeconvolution.defaultBlendingBorder[ 1 ] = (int)Math.round( gd.getNextNumber() );
			blendingBorderZ = ProcessForDeconvolution.defaultBlendingBorder[ 2 ] = (int)Math.round( gd.getNextNumber() );
			blendingRangeX = ProcessForDeconvolution.defaultBlendingRange[ 0 ] = (int)Math.round( gd.getNextNumber() );
			blendingRangeY = ProcessForDeconvolution.defaultBlendingRange[ 1 ] = (int)Math.round( gd.getNextNumber() );
			blendingRangeZ = ProcessForDeconvolution.defaultBlendingRange[ 2 ] = (int)Math.round( gd.getNextNumber() );
		}
		else
		{
			if ( ProcessForDeconvolution.defaultBlendingBorder != null && ProcessForDeconvolution.defaultBlendingBorder.length >= 3 )
			{
				blendingBorderX = ProcessForDeconvolution.defaultBlendingBorder[ 0 ];
				blendingBorderY = ProcessForDeconvolution.defaultBlendingBorder[ 1 ];
				blendingBorderZ = ProcessForDeconvolution.defaultBlendingBorder[ 2 ];
			}
			else
			{
				blendingBorderX = ProcessForDeconvolution.defaultBlendingBorderNumber;
				blendingBorderY = ProcessForDeconvolution.defaultBlendingBorderNumber;
				blendingBorderZ = ProcessForDeconvolution.defaultBlendingBorderNumber;
			}
			
			if ( ProcessForDeconvolution.defaultBlendingRange != null && ProcessForDeconvolution.defaultBlendingRange.length >= 3 )
			{
				blendingRangeX = ProcessForDeconvolution.defaultBlendingRange[ 0 ];
				blendingRangeY = ProcessForDeconvolution.defaultBlendingRange[ 1 ];
				blendingRangeZ = ProcessForDeconvolution.defaultBlendingRange[ 2 ];
			}
			else
			{
				blendingRangeX = ProcessForDeconvolution.defaultBlendingRangeNumber;
				blendingRangeY = ProcessForDeconvolution.defaultBlendingRangeNumber;
				blendingRangeZ = ProcessForDeconvolution.defaultBlendingRangeNumber;				
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
			
			final int oldX = defaultPSFSizeX;
			final int oldY = defaultPSFSizeY;
			final int oldZ = defaultPSFSizeZ;
			
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

			// update the borders if applicable
			if ( ProcessForDeconvolution.defaultBlendingBorder == null || ProcessForDeconvolution.defaultBlendingBorder.length < 3 ||
				 ( oldX/2 == ProcessForDeconvolution.defaultBlendingBorder[ 0 ] && oldY/2 == ProcessForDeconvolution.defaultBlendingBorder[ 1 ] && oldZ/5 == ProcessForDeconvolution.defaultBlendingBorder[ 2 ] ) )
			{
				ProcessForDeconvolution.defaultBlendingBorder = new int[]{ psfSizeX/2, psfSizeY/2, psfSizeZ/5 };
			}			
		}
		else
		{
			extractPSF = false;
			this.extractPSFLabels = new HashMap< Channel, ChannelPSF >();
			for ( final Channel c : channelsToProcess )
				this.extractPSFLabels.put( c, new ChannelPSF( c ) );

			final GenericDialogPlus gd = new GenericDialogPlus( "Load PSF File ..." );

			gd.addCheckbox( "Use_same_PSF_for_all_views", defaultSamePSFForAllViews );
			gd.addCheckbox( "Use_same_PSF_for_all_channels", defaultSamePSFForAllChannels );
			
			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			final GenericDialogPlus gd2 = new GenericDialogPlus( "Select PSF File ..." );
			
			defaultSamePSFForAllViews = gd.getNextBoolean();
			defaultSamePSFForAllChannels = gd.getNextBoolean();
					
			gd2.addCheckbox( "Transform_PSFs", defaultTransformPSFs );
			gd2.addMessage( "" );
			gd2.addMessage( "Note: the calibration of the PSF(s) has to match the calibration of the input views\n" +
					"if you choose to transform them according to the registration of the views!", GUIHelper.mediumstatusfont );

			int numPSFs;
			
			if ( defaultSamePSFForAllViews )
				numPSFs = 1;
			else
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

			if ( defaultSamePSFForAllViews )
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
				
			psfFiles = new HashMap< Channel, ArrayList< String > >();
			
			if ( defaultSamePSFForAllViews )
			{
				if ( defaultSamePSFForAllChannels )
				{
					// as many times the same filename as there are illuminations and angles
					final ArrayList< String > files = new ArrayList< String >();
					for ( int i = 0; i < anglesToProcess.size() * illumsToProcess.size(); ++i )
						files.add( defaultPSFFileField.get( 0 ) );

					for ( final Channel c : channelsToProcess )
						psfFiles.put( c, files );
				}
				else
				{
					int j = 0;
					
					for ( final Channel c : channelsToProcess )
					{
						final ArrayList< String > files = new ArrayList< String >();

						// as many times the same filename as there are illuminations and angles
						for ( int i = 0; i < anglesToProcess.size() * illumsToProcess.size(); ++i )
							files.add( defaultPSFFileField.get( j ) );

						psfFiles.put( c, files );
						++j;
					}
				}
			}
			else
			{
				if ( defaultSamePSFForAllChannels )
				{
					final ArrayList< String > files = new ArrayList< String >();
					files.addAll( defaultPSFFileField );
					
					for ( final Channel c : channelsToProcess )
						psfFiles.put( c, files );
				}
				else
				{
					int j = 0;
					
					for ( final Channel c : channelsToProcess )
					{
						final ArrayList< String > files = new ArrayList< String >();
						
						for ( int i = 0; i < anglesToProcess.size() * illumsToProcess.size(); ++i )
							files.add( defaultPSFFileField.get( j++ ) );
						
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
		if ( justShowWeights )
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
			
			LRFFT.cuda = NativeLibraryTools.loadNativeLibrary( potentialNames, CUDAFourierConvolution.class );

			if ( LRFFT.cuda == null )
			{
				IOFunctions.println( "Cannot load CUDA JNA library." );
				return false;
			}
			
			final ArrayList< CUDADevice > selectedDevices = CUDATools.queryCUDADetails( LRFFT.cuda, useBlocks );
			
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
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );

						// this happens only if a viewsetup is not present in any timepoint
						// (e.g. after appending fusion to a dataset)
						if ( viewId == null )
							continue;

						final ViewDescription desc = spimData.getSequenceDescription().getViewDescription( viewId ); 
						
						if ( desc.isPresent() )
						{
							// how many views with correspondences should be there
							++countViews;

							// the object with links to all available detections
							final ViewInterestPointLists vpl = vp.getViewInterestPointLists( viewId );
							
							// the list of all available detections
							for ( final String label : vpl.getHashMap().keySet() )
							{
								final InterestPointList ipl = vpl.getInterestPointList( label );

								final String name = label + " --- channel: " + c.getName() + " angle: " + a.getName() + " illum: " + i.getName() + 
										" timepoint: " + t.getName() + ": ";

								if ( ipl.getInterestPoints().size() == 0 )
									ipl.loadInterestPoints();
								
								if ( ipl.getCorrespondingInterestPoints().size() == 0 )
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
	protected Map< ViewSetup, ViewSetup > createNewViewSetups( final BoundingBox bb )
	{
		return WeightedAverageFusion.assembleNewViewSetupsFusion(
				spimData,
				channelsToProcess,
				illumsToProcess,
				anglesToProcess,
				bb,
				"Decon",
				"Decon" );	}
}
