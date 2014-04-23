package spim.process.fusion.deconvolution;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.GenericDialog;

import java.awt.Choice;
import java.util.ArrayList;
import java.util.HashMap;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.postprocessing.deconvolution2.CUDAConvolution;
import mpicbg.spim.postprocessing.deconvolution2.LRFFT;
import mpicbg.spim.postprocessing.deconvolution2.LRFFT.PSFTYPE;
import spim.fiji.plugin.GUIHelper;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.fusion.export.ImgExport;

import com.sun.jna.Native;

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
	public static int defaultDebugInterval = 1;
	public static double defaultOSEMspeedup = 1;
	public static boolean defaultOnePSFForAll = true;
	public static boolean defaultTransformPSFs = true;
	public static ArrayList< String > defaultPSFFileField = null;

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
	
	boolean useBlocks;
	int[] blockSize;
	boolean useCUDA;
	int debugInterval;
	double osemSpeedUp;
	boolean extractPSF;
	boolean transformPSFs;
	ArrayList< String > psfFiles;
	
	/**
	 * -1 == CPU
	 * 0 ... n == CUDA device i
	 */
	ArrayList< Integer > deviceList = null;
	
	/**
	 * 0 ... n == index for i'th CUDA device
	 * n + 1 == CPU
	 */
	public static ArrayList< Boolean > deviceChoice = null;
	public static int standardDevice = 10000;

	Choice gpu, block, it;
	
	public EfficientBayesianBased(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess )
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
		// TODO Auto-generated method stub
		return false;
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
		
		// check OSEM
		if ( !getOSEM() )
			return false;
		
		// check debug interval
		if ( !getDebug() )
			return false;

		return true;
	}

	@Override
	public void queryAdditionalParameters( final GenericDialog gd )
	{
		gd.addChoice( "Type_of_iteration", iterationTypeString, iterationTypeString[ defaultIterationType ] );
		it = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "OSEM_acceleration", osemspeedupChoice, osemspeedupChoice[ defaultOSEMspeedupIndex ] );
		gd.addNumericField( "Number_of_iterations", defaultNumIterations, 0 );
		gd.addCheckbox( "Use_Tikhonov_regularization", defaultUseTikhonovRegularization );
		gd.addNumericField( "Tikhonov_parameter", defaultLambda, 4 );
		gd.addChoice( "Compute", blocksChoice, blocksChoice[ defaultBlockSizeIndex ] );
		block = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "Compute_on", computationOnChoice, computationOnChoice[ defaultComputationTypeIndex ] );
		gpu = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "PSF_estimation", extractPSFChoice, extractPSFChoice[ defaultExtractPSF ] );
		gd.addChoice( "PSF_display", displayPSFChoice, displayPSFChoice[ defaultDisplayPSF ] );
		gd.addCheckbox( "Debug_mode", defaultDebugMode );
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
		useTikhonovRegularization = defaultUseTikhonovRegularization = gd.getNextBoolean();
		lambda = defaultLambda = gd.getNextNumber();
		blockSizeIndex = defaultBlockSizeIndex = gd.getNextChoiceIndex();
		computationTypeIndex = defaultComputationTypeIndex = gd.getNextChoiceIndex();
		extractPSFIndex = defaultExtractPSF = gd.getNextChoiceIndex();
		displayPSF = defaultDisplayPSF = gd.getNextChoiceIndex();
		debugMode = defaultDebugMode = gd.getNextBoolean();

		return true;
	}

	@Override
	public Fusion newInstance(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess)
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
			return fusedSizeMB;
		
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
		
	protected boolean getPSF()
	{
		if ( extractPSFIndex == 0 )
		{
			final HashMap< Channel, ArrayList< Correspondence > > correspondences = new HashMap< Channel, ArrayList< Correspondence > >();
			final HashMap< Channel, Integer > viewsPresent = new HashMap< Channel, Integer >();

			assembleAvailableCorrespondences( correspondences, viewsPresent );
			
			final String[] listOfDetections;
			
			final GenericDialogPlus gd = new GenericDialogPlus( "Extract PSF's ..." );
			
			for ( final Channel c : channelsToProcess )
				gd.addChoice( "Detections_to_extract_PSF_for_channel_" + c.getName(), arg1, arg2 );
			
			gd.showDialog();
			
			extractPSF = true;
		}
		else
		{
			extractPSF = false;

			final GenericDialogPlus gd = new GenericDialogPlus( "Load PSF File ..." );

			gd.addCheckbox( "Use same PSF for all views", defaultOnePSFForAll );
			
			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			defaultOnePSFForAll = gd.getNextBoolean();			
			
			final GenericDialogPlus gd2 = new GenericDialogPlus( "Select PSF File ..." );
			
			gd2.addCheckbox( "Transform_PSFs", defaultTransformPSFs );
			gd2.addMessage( "" );
			gd2.addMessage( "Note: the calibration of the PSF(s) has to match\n" +
					"the calibration of the input views if you choose\n" +
					"to transform them according to the registration of\n" +
					"the views!", GUIHelper.mediumstatusfont );

			int numPSFs;
			
			if ( defaultOnePSFForAll )
				numPSFs = 1;
			else
				numPSFs = anglesToProcess.size() * illumsToProcess.size();

			if ( defaultPSFFileField == null )
				defaultPSFFileField = new ArrayList<String>();

			while( defaultPSFFileField.size() < numPSFs )
				defaultPSFFileField.add( "" );

			if ( defaultPSFFileField.size() > numPSFs )
				for ( int i = numPSFs; i < defaultPSFFileField.size(); ++i )
					defaultPSFFileField.remove( numPSFs );

			if ( defaultOnePSFForAll )
			{
				gd2.addFileField( "PSF_file", defaultPSFFileField.get( 0 ) );
			}
			else
			{
				int j = 0;
				
				for ( final Angle a : anglesToProcess )
					for ( final Illumination i : illumsToProcess )
						gd2.addFileField( "PSF_file_(angle=" + a.getName() + ", illum=" + i.getName() + ")", defaultPSFFileField.get( j++ ) );
			}
			
			gd2.showDialog();
			
			if ( gd2.wasCanceled() )
				return false;

			transformPSFs = defaultTransformPSFs = gd2.getNextBoolean();
			
			defaultPSFFileField.clear();
			
			for ( int i = 0; i < numPSFs; ++i )
				defaultPSFFileField.add( gd2.getNextString() );
				
			psfFiles = new ArrayList<String>();
			if ( defaultOnePSFForAll )
			{
				for ( int i = 0; i < numPSFs; ++i )
					psfFiles.add( defaultPSFFileField.get( 0 ) );
			}
			else
			{
				psfFiles.addAll( defaultPSFFileField );
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
		deviceList = new ArrayList<Integer>();
		
		if ( computationTypeIndex == 0 )
		{
			useCUDA = false;
			deviceList.add( -1 );
		}
		else
		{
			// well, do some testing first
			try
			{
		        //String fijiDir = new File( "names.txt" ).getAbsoluteFile().getParentFile().getAbsolutePath();
		        //IJ.log( "Fiji directory: " + fijiDir );
				//LRFFT.cuda = (CUDAConvolution) Native.loadLibrary( fijiDir  + File.separator + "libConvolution3D_fftCUDAlib.so", CUDAConvolution.class );
				
				// under linux automatically checks lib/linux64
		        LRFFT.cuda = (CUDAConvolution) Native.loadLibrary( "Convolution3D_fftCUDAlib", CUDAConvolution.class );
			}
			catch (UnsatisfiedLinkError e )
			{
				IJ.log( "Cannot find CUDA JNA library: " + e );
				return false;
			}
			
			final int numDevices = LRFFT.cuda.getNumDevicesCUDA();
			
			if ( numDevices == 0 )
			{
				IJ.log( "No CUDA devices detected, only CPU will be available." );
			}
			else
			{
				IJ.log( "numdevices = " + numDevices );
				
				// yes, CUDA is possible
				useCUDA = true;
			}
			
			//
			// get the ID's and functionality of the CUDA GPU's
			//
			final String[] devices = new String[ numDevices ];
			final byte[] name = new byte[ 256 ];
			int highestComputeCapability = 0;
			long highestMemory = 0;

			int highestComputeCapabilityDevice = -1;
			
			for ( int i = 0; i < numDevices; ++i )
			{		
				LRFFT.cuda.getNameDeviceCUDA( i, name );
				
				devices[ i ] = "GPU_" + (i+1) + " of " + numDevices  + ": ";
				for ( final byte b : name )
					if ( b != 0 )
						devices[ i ] = devices[ i ] + (char)b;
				
				devices[ i ].trim();
				
				final long mem = LRFFT.cuda.getMemDeviceCUDA( i );	
				final int compCap =  10*LRFFT.cuda.getCUDAcomputeCapabilityMajorVersion( i ) + LRFFT.cuda.getCUDAcomputeCapabilityMinorVersion( i );
				
				if ( compCap > highestComputeCapability )
				{
					highestComputeCapability = compCap;
				    highestComputeCapabilityDevice = i;
				}
				
				if ( mem > highestMemory )
				{
					highestMemory = mem;
				}
				
				devices[ i ] = devices[ i ] + " (" + mem/(1024*1024) + " MB, CUDA capability " + LRFFT.cuda.getCUDAcomputeCapabilityMajorVersion( i )  + "." + LRFFT.cuda.getCUDAcomputeCapabilityMinorVersion( i ) + ")";
				//devices[ i ] = devices[ i ].replaceAll( " ", "_" );
			}
			
			// get the CPU specs
			final String cpuSpecs = "CPU (" + Runtime.getRuntime().availableProcessors() + " cores, " + Runtime.getRuntime().maxMemory()/(1024*1024) + " MB RAM available)";
			
			// if we use blocks, it makes sense to run more than one device
			if ( useBlocks )
			{
				// make a list where all are checked if there is no previous selection
				if ( deviceChoice == null || deviceChoice.size() != devices.length + 1 )
				{
					deviceChoice = new ArrayList<Boolean>( devices.length + 1 );
					for ( int i = 0; i < devices.length; ++i )
						deviceChoice.add( true );
					
					// CPU is by default not checked
					deviceChoice.add( false );
				}
				
				final GenericDialog gdCUDA = new GenericDialog( "Choose CUDA/CPUs devices to use" );
				
				for ( int i = 0; i < devices.length; ++i )
					gdCUDA.addCheckbox( devices[ i ], deviceChoice.get( i ) );
	
				gdCUDA.addCheckbox( cpuSpecs, deviceChoice.get( devices.length ) );			
				gdCUDA.showDialog();
				
				if ( gdCUDA.wasCanceled() )
					return false;
	
				// check all CUDA devices
				for ( int i = 0; i < devices.length; ++i )
				{
					if( gdCUDA.getNextBoolean() )
					{
						deviceList.add( i );
						deviceChoice.set( i , true );
					}
					else
					{
						deviceChoice.set( i , false );
					}
				}
				
				// check the CPUs
				if ( gdCUDA.getNextBoolean() )
				{
					deviceList.add( -1 );
					deviceChoice.set( devices.length , true );
				}
				else
				{
					deviceChoice.set( devices.length , false );				
				}
				
				for ( final int i : deviceList )
				{
					if ( i >= 0 )
						IJ.log( "Using device " + devices[ i ] );
					else if ( i == -1 )
						IJ.log( "Using device " + cpuSpecs );
				}
				
				if ( deviceList.size() == 0 )
				{
					IJ.log( "You selected no device, quitting." );
					return false;
				}
			}
			else
			{
				// only choose one device to run everything at once				
				final GenericDialog gdCUDA = new GenericDialog( "Choose CUDA device" );

				if ( standardDevice >= devices.length )
					standardDevice = highestComputeCapabilityDevice;
				
				gdCUDA.addChoice( "Device", devices, devices[ standardDevice ] );
				
				gdCUDA.showDialog();
			
				if ( gdCUDA.wasCanceled() )
					return false;
				
				deviceList.add( standardDevice = gdCUDA.getNextChoiceIndex() );
				IJ.log( "Using device " + devices[ deviceList.get( 0 ) ] );
			}
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
				for ( final Angle a : anglesToProcess )
					for ( final Illumination i : illumsToProcess )
					{
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );
						final ViewDescription<TimePoint, ViewSetup> desc = spimData.getSequenceDescription().getViewDescription( viewId ); 
						
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
			
			for ( final Correspondence corr : correspondences.get( c ) )
			{
				final int numViews = viewsPresent.get( c );
				IOFunctions.println( "Label '" + corr.getLabel() + "' (channel " + c.getName() + ") has " + corr.getCount() + "/" + numViews + " views with corresponding detections." );
				
				if ( corr.getCount() != numViews )
					correspondences.remove( corr );
			}
			
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
}
