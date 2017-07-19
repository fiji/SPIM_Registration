package spim.fiji.plugin.interestpointdetection;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ij.ImagePlus;
import ij.gui.GenericDialog;
import mpicbg.models.RigidModel3D;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.InteractiveDoG;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.util.GenericDialogAppender;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.popup.DisplayFusedImagesPopup;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDASeparableConvolution;
import spim.process.cuda.CUDATools;
import spim.process.cuda.NativeLibraryTools;
import spim.process.fusion.FusionTools;
import spim.process.fusion.FusionTools.ImgDataType;
import spim.process.interestpointdetection.methods.dog.DoG;
import spim.process.interestpointdetection.methods.dog.DoGParameters;
import spim.process.interestpointdetection.methods.downsampling.DownsampleTools;
import spim.process.interestpointregistration.TransformationTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class DifferenceOfGaussianGUI extends DifferenceOfGUI implements GenericDialogAppender
{
	public static double defaultUseGPUMem = 75;

	public static double defaultSigma = 1.8;
	public static double defaultThreshold = 0.008;
	public static boolean defaultFindMin = false;
	public static boolean defaultFindMax = true;

	public static String[] computationOnChoice = new String[]{
		"CPU (Java)",
		"GPU approximate (Nvidia CUDA via JNA)",
		"GPU accurate (Nvidia CUDA via JNA)" };
	public static int defaultComputationChoiceIndex = 0;

	double sigma;
	double threshold;
	boolean findMin;
	boolean findMax;

	double percentGPUMem = defaultUseGPUMem;

	/**
	 * 0 ... n == CUDA device i
	 */
	ArrayList< CUDADevice > deviceList = null;
	CUDASeparableConvolution cuda = null;
	boolean accurateCUDA = false;

	public DifferenceOfGaussianGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription() { return "Difference-of-Gaussian"; }

	@Override
	public DifferenceOfGaussianGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new DifferenceOfGaussianGUI( spimData, viewIdsToProcess );
	}

	@Override
	public HashMap< ViewId, List< InterestPoint > > findInterestPoints( final TimePoint t )
	{
		final DoGParameters dog = new DoGParameters();

		dog.imgloader = spimData.getSequenceDescription().getImgLoader();
		dog.toProcess = new ArrayList< ViewDescription >();

		dog.localization = this.localization;
		dog.downsampleZ = this.downsampleZ;
		dog.imageSigmaX = this.imageSigmaX;
		dog.imageSigmaY = this.imageSigmaY;
		dog.imageSigmaZ = this.imageSigmaZ;

		dog.minIntensity = this.minIntensity;
		dog.maxIntensity = this.maxIntensity;

		dog.sigma = this.sigma;
		dog.threshold = this.threshold;
		dog.findMin = this.findMin;
		dog.findMax = this.findMax;

		dog.cuda = this.cuda;
		dog.deviceList = this.deviceList;
		dog.accurateCUDA = this.accurateCUDA;
		dog.percentGPUMem = this.percentGPUMem;

		dog.limitDetections = this.limitDetections;
		dog.maxDetections = this.maxDetections;
		dog.maxDetectionsTypeIndex = this.maxDetectionsTypeIndex;

		final HashMap< ViewId, List< InterestPoint > > interestPoints = new HashMap< ViewId, List< InterestPoint > >();

		for ( final ViewDescription vd : SpimData2.getAllViewIdsForTimePointSorted( spimData, viewIdsToProcess, t ) )
		{
			// make sure not everything crashes if one file is missing
			try
			{
				if ( !vd.isPresent() )
					continue;

				dog.toProcess.clear();
				dog.toProcess.add( vd );

				// downsampleXY == 0 : a bit less then z-resolution
				// downsampleXY == -1 : a bit more then z-resolution
				if ( downsampleXYIndex < 1 )
					dog.downsampleXY = DownsampleTools.downsampleFactor( downsampleXYIndex, downsampleZ, vd.getViewSetup().getVoxelSize() );
				else
					dog.downsampleXY = downsampleXYIndex;

				DoG.addInterestPoints( interestPoints, dog );
			}
			catch ( Exception  e )
			{
				IOFunctions.println( "An error occured (DOG): " + e ); 
				IOFunctions.println( "Failed to segment viewId: " + Group.pvid( vd ) + ". Continuing with next one." );
				e.printStackTrace();
			}
		}

		return interestPoints;
	}

	@Override
	protected boolean setDefaultValues( final int brightness )
	{
		this.sigma = defaultSigma;
		this.findMin = false;
		this.findMax = true;

		if ( brightness == 0 )
			this.threshold = 0.001;
		else if ( brightness == 1 )
			this.threshold = 0.008;
		else if ( brightness == 2 )
			this.threshold = 0.03;
		else if ( brightness == 3 )
			this.threshold = 0.1;
		else
			return false;
		
		return true;
	}

	@Override
	protected boolean setAdvancedValues()
	{
		final GenericDialog gd = new GenericDialog( "Advanced values" );

		gd.addNumericField( "Sigma", defaultSigma, 8 );
		gd.addNumericField( "Threshold", defaultThreshold, 8 );
		gd.addCheckbox( "Find_minima", defaultFindMin );
		gd.addCheckbox( "Find_maxima", defaultFindMax );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		this.sigma = defaultSigma = gd.getNextNumber();
		this.threshold = defaultThreshold = gd.getNextNumber();
		this.findMin = defaultFindMin = gd.getNextBoolean();
		this.findMax = defaultFindMax = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues()
	{
		RandomAccessibleInterval< FloatType > img;
		final String title;

		if ( !groupIllums && !groupTiles )
		{
			final ViewId view = getViewSelection( "Interactive Difference-of-Gaussian", "Please select view to use" );
			
			if ( view == null )
				return false;
	
			final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( view.getTimePointId(), view.getViewSetupId() );
	
			// downsampleXY == 0 : a bit less then z-resolution
			// downsampleXY == -1 : a bit more then z-resolution
			final int downsampleXY;
	
			if ( downsampleXYIndex < 1 )
				downsampleXY = DownsampleTools.downsampleFactor( downsampleXYIndex, downsampleZ, viewDescription.getViewSetup().getVoxelSize() );
			else
				downsampleXY = downsampleXYIndex;

			img = DownsampleTools.openAndDownsample(
				spimData.getSequenceDescription().getImgLoader(),
				viewDescription,
				new AffineTransform3D(),
				downsampleXY,
				downsampleZ );

			if ( img == null )
			{
				IOFunctions.println( "View not found: " + viewDescription );
				return false;
			}

			title = "tp: " + viewDescription.getTimePoint().getName() + " viewSetup: " + viewDescription.getViewSetupId();
		}
		else
		{
			final List< Group< ViewDescription > > groups = getGroups();
			final Group< ViewDescription > group = getGroupSelection( "Interactive Difference-of-Gaussian", "Please select grouped views to use", groups );

			if ( group == null )
				return false;

			// use the first view that comes along to determine downsampling

			// downsampleXY == 0 : a bit less then z-resolution
			// downsampleXY == -1 : a bit more then z-resolution
			final int downsampleXY;

			final ViewDescription randomView = group.iterator().next();

			if ( downsampleXYIndex < 1 )
				downsampleXY = DownsampleTools.downsampleFactor( downsampleXYIndex, downsampleZ, randomView.getViewSetup().getVoxelSize() );
			else
				downsampleXY = downsampleXYIndex;

			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Desired downsampling in XY " + downsampleXY + "x ..." );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Desired downsampling in Z " + downsampleZ + "x ..." );

			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Identifying corresponding scaling of fused dataset using view " + Group.pvid( randomView ) );

			final NumberFormat f = TransformationTools.f;

			final Dimensions dim = spimData.getSequenceDescription().getViewDescription( randomView ).getViewSetup().getSize();
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( randomView );
			vr.updateModel();
			final AffineTransform3D transform = vr.getModel().copy();

			System.out.println( "Original: " + TransformationTools.printAffine3D( transform ) );

			// mapping back the image first to the origin using a rigid model
			final AffineTransform3D mapBack = TransformationTools.computeMapBackModel(
					dim,
					new AffineTransform3D(), // identity
					TransformationTools.getModel( transform ), // current model
					new RigidModel3D() ); // what to use

			System.out.println( "MapBack: " + TransformationTools.printAffine3D( mapBack ) );

			final AffineTransform3D atOrigin = transform.preConcatenate( mapBack );

			System.out.println( "At origin: " + TransformationTools.printAffine3D( atOrigin ) );

			// there seems to be a bug in Transform3D, it does mix up the y/z dimensions sometimes
			// TransformationTools.getScaling( atOrigin, scale );

			// the scale is approximately now the diagonal entries in the matrix
			final double[] scale = new double[ 3 ];
			scale[ 0 ] = atOrigin.get( 0, 0 );
			scale[ 1 ] = atOrigin.get( 1, 1 );
			scale[ 2 ] = atOrigin.get( 2, 2 );

			IOFunctions.println( "View is currently scaled by: ( " + f.format( scale[ 0 ] ) + ", " + f.format( scale[ 1 ] ) + ", " + f.format( scale[ 2 ] ) + ")" );

			final double[] targetDS = new double[ 3 ];
			targetDS[ 0 ] = ( 1.0 / downsampleXY ) / ( ( scale[ 0 ] + scale[ 1 ] ) / 2.0 );
			targetDS[ 1 ] = ( 1.0 / downsampleXY ) / ( ( scale[ 0 ] + scale[ 1 ] ) / 2.0 );
			targetDS[ 2 ] = ( 1.0 / downsampleZ ) / scale[ 2 ];

			IOFunctions.println( "Fused image must be downsampled by: ( " + f.format( 1.0/targetDS[ 0 ] ) + ", " + f.format( 1.0/targetDS[ 1 ] ) + ", " + f.format( 1.0/targetDS[ 2 ] ) + ") after applying mapback." );

			
			//FusionTools.fuseVirtual( spimData, views, DisplayFusedImagesPopup.defaultUseBlending, false, DisplayFusedImagesPopup.defaultInterpolation, bb, downsampling );

			img = null;

			title = nameForGroup( group );

			return false;
		}



		final ImagePlus imp = ImageJFunctions.wrapFloat( img, "" ).duplicate();
		imp.resetDisplayRange();
		img = null;
		imp.setDimensions( 1, imp.getStackSize(), 1 );
		imp.setTitle( title );
		imp.show();
		imp.setSlice( imp.getStackSize() / 2 );
		imp.setRoi( 0, 0, imp.getWidth()/3, imp.getHeight()/3 );

		final InteractiveDoG idog = new InteractiveDoG( imp );

		idog.setSigma2isAdjustable( false );
		idog.setInitialSigma( (float)defaultSigma );
		idog.setThreshold( (float)defaultThreshold );
		idog.setLookForMinima( defaultFindMin );
		idog.setLookForMaxima( defaultFindMax );
		idog.setMinIntensityImage( minIntensity ); // if is Double.NaN will be ignored
		idog.setMaxIntensityImage( maxIntensity ); // if is Double.NaN will be ignored

		idog.run( null );
		
		while ( !idog.isFinished() )
		{
			try
			{
				Thread.sleep( 100 );
			}
			catch (InterruptedException e) {}
		}

		imp.close();

		if ( idog.wasCanceled() )
			return false;

		this.sigma = defaultSigma = idog.getInitialSigma();
		this.threshold = defaultThreshold = idog.getThreshold();
		this.findMin = defaultFindMin = idog.getLookForMinima();
		this.findMax = defaultFindMax = idog.getLookForMaxima();

		return true;
	}

	@Override
	public String getParameters()
	{
		return "DOG s=" + sigma + " t=" + threshold + " min=" + findMin + " max=" + findMax +
				" imageSigmaX=" + imageSigmaX + " imageSigmaY=" + imageSigmaY + " imageSigmaZ=" + imageSigmaZ + " downsampleXYIndex=" + downsampleXYIndex +
				" downsampleZ=" + downsampleZ + " minIntensity=" + minIntensity + " maxIntensity=" + maxIntensity;
	}

	@Override
	protected void addAddtionalParameters( final GenericDialog gd )
	{
		gd.addChoice( "Compute_on", computationOnChoice, computationOnChoice[ defaultComputationChoiceIndex ] );
		
	}

	@Override
	protected boolean queryAdditionalParameters( final GenericDialog gd )
	{
		final int computationTypeIndex = defaultComputationChoiceIndex = gd.getNextChoiceIndex();

		if ( computationTypeIndex == 1 )
			accurateCUDA = false;
		else
			accurateCUDA = true;

		if ( computationTypeIndex >= 1 )
		{
			final ArrayList< String > potentialNames = new ArrayList< String >();
			potentialNames.add( "separable" );
			
			cuda = NativeLibraryTools.loadNativeLibrary( potentialNames, CUDASeparableConvolution.class );

			if ( cuda == null )
			{
				IOFunctions.println( "Cannot load CUDA JNA library." );
				deviceList = null;
				return false;
			}
			else
			{
				deviceList = new ArrayList< CUDADevice >();
			}

			// multiple CUDA devices sometimes crashes, no idea why yet ...
			final ArrayList< CUDADevice > selectedDevices = CUDATools.queryCUDADetails( cuda, false, this );

			if ( selectedDevices == null || selectedDevices.size() == 0 )
				return false;
			else
				deviceList.addAll( selectedDevices );

			// TODO: remove this, only for debug on non-CUDA machines >>>>
			if ( deviceList.get( 0 ).getDeviceName().startsWith( "CPU emulation" ) )
			{
				for ( int i = 0; i < deviceList.size(); ++i )
				{
					deviceList.set( i, new CUDADevice( -1-i, deviceList.get( i ).getDeviceName(), deviceList.get( i ).getTotalDeviceMemory(), deviceList.get( i ).getFreeDeviceMemory(), deviceList.get( i ).getMajorComputeVersion(), deviceList.get( i ).getMinorComputeVersion() ) );
					IOFunctions.println( "Running on cpu emulation, added " + ( -1-i ) + " as device" );
				}
			}
			// TODO: <<<< remove this, only for debug on non-CUDA machines
		}
		else
		{
			deviceList = null;
		}

		return true;
	}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		gd.addMessage( "" );
		gd.addSlider( "Percent_of_GPU_Memory_to_use", 1, 100, defaultUseGPUMem );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		this.percentGPUMem = defaultUseGPUMem = gd.getNextNumber();
		return true;
	}
}
