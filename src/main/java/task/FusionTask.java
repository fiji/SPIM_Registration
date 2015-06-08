package task;

import bdv.img.hdf5.Hdf5ImageLoader;
import com.sun.jna.Native;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.postprocessing.deconvolution2.LRFFT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.queryXML.HeadlessParseQueryXML;
import spim.fiji.plugin.resave.PluginHelper;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDAFourierConvolution;
import spim.process.fusion.boundingbox.PreDefinedBoundingBox;
import spim.process.fusion.deconvolution.ChannelPSF;
import spim.process.fusion.deconvolution.EfficientBayesianBased;
import spim.process.fusion.deconvolution.ProcessForDeconvolution;
import spim.process.fusion.export.AppendSpimData2;
import spim.process.fusion.export.ExportSpimData2HDF5;
import spim.process.fusion.export.ExportSpimData2TIFF;
import spim.process.fusion.export.ImgExport;
import spim.process.fusion.export.Save3dTIFF;
import spim.process.fusion.weightedavg.WeightedAverageFusion;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Headless module for Fuse task
 */
public class FusionTask extends AbstractTask
{
	// Only consider the below cases
	//
	// Compute on:
	// CPU
	// GPU

	// Fusion algorithm:
	//
	// EfficientBayesianBased
	// WeightedAverageFusion with FUSEDATA
	// WeightedAverageFusion with INDEPENDENT

	// BoudingBox algorithms:
	//
	// ManualBoundingBox

	// ImageExport options:
	//
	// Save3dTIFF
	// ExportSpimData2TIFF
	// ExportSpimData2HDF5
	// AppendSpimData2

	private static final Logger LOG = LoggerFactory.getLogger( FusionTask.class );

	public String getTitle() { return "Fusion Task"; }

	public static enum Method { EfficientBayesianBased, WeightedAverageFusionWithFUSEDATA, WeightedAverageFusionWithINDEPENDENT };

	public static enum Export { Save3dTIFF, ExportSpimData2TIFF, ExportSpimData2HDF5, AppendSpimData2 }

	public static enum Interpolation { NearestNeighbor, NLinear }

	public static class Parameters extends AbstractTask.Parameters
	{
		private Method method;
		private Export export;
		private boolean useCluster;

		private int[] min;
		private int[] max;

		private int[] blockSize;

		// EfficientByesianBased parameters
		// computeOn:
		// 0:"CPU (Java)",
		// 1:"GPU (Nvidia CUDA via JNA)",
		private int computeOn;
		private String fourierConvolutionCUDALib;

		private boolean isExtractPSF;

		private double osemSpeedup;

		private boolean isAdjustBlending;

		private int blendingBorderX;
		private int blendingBorderY;
		private int blendingBorderZ;

		private int blendingRangeX;
		private int blendingRangeY;
		private int blendingRangeZ;

		private boolean isJustShowWeights;

		private LRFFT.PSFTYPE iterationType;

		private int numOfIteration;

		private boolean useTikhonovRegularization;

		private double lambda;

		// WeightedAverageFusion parameters
		private int numParalellViews;
		private boolean useBlending;
		private boolean useContentBased;
		private Interpolation interpolation;

		public Method getMethod()
		{
			return method;
		}

		public void setMethod( Method method )
		{
			this.method = method;
		}

		public Export getExport()
		{
			return export;
		}

		public void setExport( Export export )
		{
			this.export = export;
		}

		public boolean isUseCluster()
		{
			return useCluster;
		}

		public void setUseCluster( boolean useCluster )
		{
			this.useCluster = useCluster;
		}

		public int[] getMin()
		{
			return min;
		}

		public void setMin( int[] min )
		{
			this.min = min;
		}

		public int[] getMax()
		{
			return max;
		}

		public void setMax( int[] max )
		{
			this.max = max;
		}

		public int[] getBlockSize()
		{
			return blockSize;
		}

		public void setBlockSize( int[] blockSize )
		{
			this.blockSize = blockSize;
		}

		public int getComputeOn()
		{
			return computeOn;
		}

		public void setComputeOn( int computeOn )
		{
			this.computeOn = computeOn;
		}

		public String getFourierConvolutionCUDALib()
		{
			return fourierConvolutionCUDALib;
		}

		public void setFourierConvolutionCUDALib( String fourierConvolutionCUDALib )
		{
			this.fourierConvolutionCUDALib = fourierConvolutionCUDALib;
		}

		public boolean isExtractPSF()
		{
			return isExtractPSF;
		}

		public void setExtractPSF( boolean isExtractPSF )
		{
			this.isExtractPSF = isExtractPSF;
		}

		public double getOsemSpeedup()
		{
			return osemSpeedup;
		}

		public void setOsemSpeedup( double osemSpeedup )
		{
			this.osemSpeedup = osemSpeedup;
		}

		public boolean isAdjustBlending()
		{
			return isAdjustBlending;
		}

		public void setAdjustBlending( boolean isAdjustBlending )
		{
			this.isAdjustBlending = isAdjustBlending;
		}

		public int getBlendingBorderX()
		{
			return blendingBorderX;
		}

		public void setBlendingBorderX( int blendingBorderX )
		{
			this.blendingBorderX = blendingBorderX;
		}

		public int getBlendingBorderY()
		{
			return blendingBorderY;
		}

		public void setBlendingBorderY( int blendingBorderY )
		{
			this.blendingBorderY = blendingBorderY;
		}

		public int getBlendingBorderZ()
		{
			return blendingBorderZ;
		}

		public void setBlendingBorderZ( int blendingBorderZ )
		{
			this.blendingBorderZ = blendingBorderZ;
		}

		public int getBlendingRangeX()
		{
			return blendingRangeX;
		}

		public void setBlendingRangeX( int blendingRangeX )
		{
			this.blendingRangeX = blendingRangeX;
		}

		public int getBlendingRangeY()
		{
			return blendingRangeY;
		}

		public void setBlendingRangeY( int blendingRangeY )
		{
			this.blendingRangeY = blendingRangeY;
		}

		public int getBlendingRangeZ()
		{
			return blendingRangeZ;
		}

		public void setBlendingRangeZ( int blendingRangeZ )
		{
			this.blendingRangeZ = blendingRangeZ;
		}

		public boolean isJustShowWeights()
		{
			return isJustShowWeights;
		}

		public void setJustShowWeights( boolean isJustShowWeights )
		{
			this.isJustShowWeights = isJustShowWeights;
		}

		public LRFFT.PSFTYPE getIterationType()
		{
			return iterationType;
		}

		public void setIterationType( LRFFT.PSFTYPE iterationType )
		{
			this.iterationType = iterationType;
		}

		public int getNumOfIteration()
		{
			return numOfIteration;
		}

		public void setNumOfIteration( int numOfIteration )
		{
			this.numOfIteration = numOfIteration;
		}

		public boolean isUseTikhonovRegularization()
		{
			return useTikhonovRegularization;
		}

		public void setUseTikhonovRegularization( boolean useTikhonovRegularization )
		{
			this.useTikhonovRegularization = useTikhonovRegularization;
		}

		public double getLambda()
		{
			return lambda;
		}

		public void setLambda( double lambda )
		{
			this.lambda = lambda;
		}

		public int getNumParalellViews()
		{
			return numParalellViews;
		}

		public void setNumParalellViews( int numParalellViews )
		{
			this.numParalellViews = numParalellViews;
		}

		public boolean isUseBlending()
		{
			return useBlending;
		}

		public void setUseBlending( boolean useBlending )
		{
			this.useBlending = useBlending;
		}

		public boolean isUseContentBased()
		{
			return useContentBased;
		}

		public void setUseContentBased( boolean useContentBased )
		{
			this.useContentBased = useContentBased;
		}

		public Interpolation getInterpolation()
		{
			return interpolation;
		}

		public void setInterpolation( Interpolation interpolation )
		{
			this.interpolation = interpolation;
		}
	}

	public void process( final Parameters params )
	{
		final HeadlessParseQueryXML result = new HeadlessParseQueryXML();
		result.loadXML( params.getXmlFilename(), params.isUseCluster() );

		spimData = result.getData();

		final List< ViewId > viewIdsToProcess = SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		final String clusterExtention = result.getClusterExtension();

		Fusion fusion = null;

		switch ( params.getMethod() )
		{
			case EfficientBayesianBased: fusion = processEfficientBayesianBased( params, viewIdsToProcess );
				break;
			case WeightedAverageFusionWithFUSEDATA: fusion = processWeightedAverageFusion( params, viewIdsToProcess, WeightedAverageFusion.WeightedAvgFusionType.FUSEDATA );
				break;
			case WeightedAverageFusionWithINDEPENDENT: fusion = processWeightedAverageFusion( params, viewIdsToProcess, WeightedAverageFusion.WeightedAvgFusionType.INDEPENDENT );
		}

		// Setup BoundingBox
		final PreDefinedBoundingBox boundingBox = new PreDefinedBoundingBox( spimData, viewIdsToProcess );
		boundingBox.setUpBoundingBox( fusion, params.getMin(), params.getMax() );

		// Setup Image export
		ImgExport imgExport = null;

		switch ( params.getExport() )
		{
			case Save3dTIFF: imgExport =	new Save3dTIFF(null); break;
			case ExportSpimData2TIFF: imgExport = new ExportSpimData2TIFF(); break;
			case ExportSpimData2HDF5: imgExport = new ExportSpimData2HDF5(); break;
			case AppendSpimData2: imgExport = new AppendSpimData2(); break;
		}

		if ( spimData.getSequenceDescription().getImgLoader() instanceof Hdf5ImageLoader )
			PreDefinedBoundingBox.defaultPixelType = 1; // set to 16 bit by default for hdf5

		// set all the properties required for exporting as a new XML or as addition to an existing XML
		fusion.defineNewViewSetups( boundingBox );
		imgExport.setXMLData( fusion.getTimepointsToProcess(), fusion.getNewViewSetups() );

		if ( !imgExport.queryParameters( spimData, boundingBox.getPixelType() == 1 ) )
			return;

		// did anyone modify this SpimData object?
		boolean spimDataModified = false;

		fusion.fuseData( boundingBox, imgExport );

		spimDataModified |= boundingBox.cleanUp();

		// save the XML if metadata was updated
		if ( spimData.getSequenceDescription().getImgLoader() instanceof AbstractImgLoader )
		{
			try
			{
				for ( final ViewSetup setup : spimData.getSequenceDescription().getViewSetupsOrdered() )
					spimDataModified |= ( (AbstractImgLoader)spimData.getSequenceDescription().getImgLoader() ).updateXMLMetaData( setup, false );
			}
			catch( Exception e )
			{
				LOG.warn( "Failed to update metadata, this should not happen: " + e );
			}
		}

		spimDataModified |= imgExport.finish();

		if ( spimDataModified )
		{
			if( params.isUseCluster() )
				SpimData2.saveXML( spimData, params.getXmlFilename(), clusterExtention );
			else
				SpimData2.saveXML( spimData, params.getXmlFilename(), "" );
		}

		LOG.info( "(" + new Date( System.currentTimeMillis() ) + "): Fusion finished." );
	}

	private EfficientBayesianBased processEfficientBayesianBased( final Parameters params, final List< ViewId > viewIdsToProcess )
	{
		EfficientBayesianBased ebb = new EfficientBayesianBased( spimData, viewIdsToProcess );

		ebb.setBlockSize( params.getBlockSize() );

		// GPU setup
		if ( params.getComputeOn() > 0 )
		{
			CUDAFourierConvolution cuda = (CUDAFourierConvolution) Native.loadLibrary( params.getFourierConvolutionCUDALib(), CUDAFourierConvolution.class );

			if ( cuda == null )
			{
				LOG.info( "Cannot load CUDA JNA library." );
				return null;
			}
			else
			{
				ArrayList< CUDADevice > deviceList = new ArrayList< CUDADevice >();
				final int numDevices = cuda.getNumDevicesCUDA();
				if ( numDevices == -1 )
				{
					LOG.info( "Querying CUDA devices crashed, no devices available." );
					return null;
				}
				else if ( numDevices == 0 )
				{
					LOG.info( "No CUDA devices detected." );
					return null;
				}
				else
				{
					// TODO: Support multiple GPUs
					// Use the first GPU
					final byte[] name = new byte[ 256 ];
					cuda.getNameDeviceCUDA( 0, name );
					final String deviceName = new String( name );

					final long mem = cuda.getMemDeviceCUDA( 0 );
					long freeMem;

					try
					{
						freeMem = cuda.getFreeMemDeviceCUDA( 0 );
					}
					catch ( UnsatisfiedLinkError e )
					{
						LOG.info( "Using an outdated version of the CUDA libs, cannot query free memory. Assuming total memory." );
						freeMem = mem;
					}

					final int majorVersion = cuda.getCUDAcomputeCapabilityMajorVersion( 0 );
					final int minorVersion = cuda.getCUDAcomputeCapabilityMinorVersion( 0 );

					if ( isDebug )
					{
						LOG.info( "GPU :" + deviceName );
						LOG.info( "Memory :" + freeMem );
					}

					deviceList.add( new CUDADevice( 0, deviceName, mem, freeMem, majorVersion, minorVersion ) );

					ebb.setUseCUDA( true );
					LRFFT.cuda = cuda;
					ebb.setDeviceList( deviceList );
				}
			}
		}

		ebb.setExtractPSF( params.isExtractPSF() );

		if ( ebb.isExtractPSF() )
		{
			final HashMap< Channel, ArrayList< EfficientBayesianBased.Correspondence > > correspondences = new HashMap< Channel, ArrayList< EfficientBayesianBased.Correspondence > >();

			ebb.assembleAvailableCorrespondences( correspondences, new HashMap< Channel, Integer >(), true );

			int sumChannels = 0;
			for ( final Channel c : correspondences.keySet() )
				sumChannels += correspondences.get( c ).size();

			if ( sumChannels == 0 )
			{
				LOG.warn( "No detections that have been registered are available to extract a PSF. Quitting." );
				return null;
			}

			// make a list of those labels for the imagej dialog
			// and set the default selections
			final List< Channel > channelsToProcess = ebb.getChannelsToProcess();
			final String[][] choices = new String[ channelsToProcess.size() ][];

			if ( EfficientBayesianBased.defaultPSFLabelIndex == null || EfficientBayesianBased.defaultPSFLabelIndex.length != channelsToProcess.size() )
				EfficientBayesianBased.defaultPSFLabelIndex = new int[ channelsToProcess.size() ];

			// remember which choiceindex in the dialog maps to which other channel
			final ArrayList< HashMap< Integer, Channel > > otherChannels = new ArrayList< HashMap< Integer, Channel > >();

			for ( int i = 0; i < channelsToProcess.size(); ++i )
			{
				final Channel c = channelsToProcess.get( i );
				final ArrayList< EfficientBayesianBased.Correspondence > corr = correspondences.get( c );
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

				if ( EfficientBayesianBased.defaultPSFLabelIndex[ i ] < 0 || EfficientBayesianBased.defaultPSFLabelIndex[ i ] >= choices[ i ].length )
					EfficientBayesianBased.defaultPSFLabelIndex[ i ] = 0;
			}

			ebb.setExtractPSFLabels( new HashMap< Channel, ChannelPSF >() );

			for ( int j = 0; j < channelsToProcess.size(); ++j )
			{
				final Channel c = channelsToProcess.get( j );
				final int l = EfficientBayesianBased.defaultPSFLabelIndex[ j ];

				if ( l < correspondences.get( c ).size() )
				{
					ebb.getExtractPSFLabels().put( c, new ChannelPSF( c, choices[ j ][ l ] ) );
					LOG.info( "Channel " + c.getName() + ": extract PSF from label '" + choices[ j ][ l ] + "'" );
				}
				else
				{
					ebb.getExtractPSFLabels().put( c, new ChannelPSF( c, otherChannels.get( j ).get( l ) ) );
					LOG.info( "Channel " + c.getName() + ": uses same PSF as channel " + ebb.getExtractPSFLabels().get( c ).getOtherChannel().getName() );
				}
			}

			final int oldX = EfficientBayesianBased.defaultPSFSizeX;
			final int oldY = EfficientBayesianBased.defaultPSFSizeY;
			final int oldZ = EfficientBayesianBased.defaultPSFSizeZ;

			int psfSizeX = EfficientBayesianBased.defaultPSFSizeX;
			int psfSizeY = EfficientBayesianBased.defaultPSFSizeY;
			int psfSizeZ = EfficientBayesianBased.defaultPSFSizeZ;

			// enforce odd number
			if ( psfSizeX % 2 == 0 )
				EfficientBayesianBased.defaultPSFSizeX = ++psfSizeX;

			if ( psfSizeY % 2 == 0 )
				EfficientBayesianBased.defaultPSFSizeY = ++psfSizeY;

			if ( psfSizeZ % 2 == 0 )
				EfficientBayesianBased.defaultPSFSizeZ = ++psfSizeZ;

			// update the borders if applicable
			if ( ProcessForDeconvolution.defaultBlendingBorder == null || ProcessForDeconvolution.defaultBlendingBorder.length < 3 ||
					( oldX/2 == ProcessForDeconvolution.defaultBlendingBorder[ 0 ] && oldY/2 == ProcessForDeconvolution.defaultBlendingBorder[ 1 ] && oldZ/5 == ProcessForDeconvolution.defaultBlendingBorder[ 2 ] ) )
			{
				ProcessForDeconvolution.defaultBlendingBorder = new int[]{ psfSizeX/2, psfSizeY/2, psfSizeZ/5 };
			}
		}
		else
		{
			// TODO: Get PSF Files from string parameters
		}

		// reorder the channels so that those who extract a PSF
		// from the images for a certain timepoint will be processed
		// first
		if ( ebb.isExtractPSF() )
			if ( !ebb.reOrderChannels() )
				return null;

		// check OSEM
		ebb.setOsemSpeedUp( params.getOsemSpeedup() );

		// get the blending parameters
		ebb.setAdjustBlending( params.isAdjustBlending() );

		if ( params.isAdjustBlending() )
		{
			ebb.setBlendingBorderX( params.getBlendingBorderX() );
			ebb.setBlendingBorderY( params.getBlendingBorderY() );
			ebb.setBlendingBorderZ( params.getBlendingBorderZ() );

			ebb.setBlendingRangeX( params.getBlendingRangeX() );
			ebb.setBlendingRangeY( params.getBlendingRangeY() );
			ebb.setBlendingBorderZ( params.getBlendingBorderZ() );
		}

		if ( params.isJustShowWeights() )
		{
			ebb.setJustShowWeights( true );
		}
		else
		{
			ebb.setIterationType( params.getIterationType() );
		}

		ebb.setNumIterations( params.getNumOfIteration() );

		ebb.setUseTikhonovRegularization( params.isUseTikhonovRegularization() );

		ebb.setLambda( params.getLambda() );

		// Do not show PSF for headless mode
		ebb.setDisplayPSF( 0 );

		return ebb;
	}

	public WeightedAverageFusion processWeightedAverageFusion( final Parameters params, final List< ViewId > viewIdsToProcess, final WeightedAverageFusion.WeightedAvgFusionType fusionType )
	{
		WeightedAverageFusion fusion = new WeightedAverageFusion( spimData, viewIdsToProcess, fusionType );

		if ( fusionType == WeightedAverageFusion.WeightedAvgFusionType.FUSEDATA )
		{
			fusion.setNumParalellViews( params.getNumParalellViews() );
			fusion.setUseBlending( params.isUseBlending() );
			fusion.setUseContentBased( params.isUseContentBased() );
		}
		else
		{
			fusion.setUseBlending( false );
			fusion.setUseContentBased( false );
		}

		switch ( params.getInterpolation() )
		{
			case NearestNeighbor: fusion.setInterpolation( 0 ); break;
			case NLinear: fusion.setInterpolation( 1 ); break;
		}

		return fusion;
	}

	private Parameters getParams( final String[] args )
	{
		final Properties props = parseArgument( "Fusion", getTitle(), args );

		final Parameters params = new Parameters();
		params.setXmlFilename( props.getProperty( "xml_filename" ) );
		params.setUseCluster( Boolean.parseBoolean( props.getProperty( "use_cluster", "false" ) ) );

		final String method = props.getProperty( "method" );

		if( method != null )
		{
			params.setMethod( Method.valueOf( method ) );
		}

		final String export = props.getProperty( "export" );

		if( export != null )
		{
			params.setExport( Export.valueOf( export ) );
		}

		params.setComputeOn( Integer.parseInt( props.getProperty( "compute_on", "0" ) ) );
		params.setFourierConvolutionCUDALib( props.getProperty( "fourier_convolution_cuda_lib" ) );
		// Setup fusion attributes
		// The below data should be given by users
		// 1: {64, 64, 64}
		// 0: CPU
		// 1: PSFTYPE.OPTIMIZATION_I
		//  {64, 64, 64}
		params.setBlockSize( PluginHelper.parseArrayIntegerString( props.getProperty( "block_size" ) ) );

		params.setExtractPSF( Boolean.parseBoolean( props.getProperty( "extract_psf", "true" ) ) );

		params.setOsemSpeedup( Double.parseDouble( props.getProperty( "osem_speedup", "1.0" ) ) );

		params.setMin( PluginHelper.parseArrayIntegerString( props.getProperty( "min" ) ) );

		params.setMax( PluginHelper.parseArrayIntegerString( props.getProperty( "max" ) ) );

		params.setAdjustBlending( Boolean.parseBoolean( props.getProperty( "adjust_blending", "false" ) ) );

		if( params.isAdjustBlending() )
		{
			params.setBlendingBorderX( Integer.parseInt( props.getProperty( "blending_border_x" ) ) );
			params.setBlendingBorderY( Integer.parseInt( props.getProperty( "blending_border_y" ) ) );
			params.setBlendingBorderZ( Integer.parseInt( props.getProperty( "blending_border_z" ) ) );

			params.setBlendingRangeX( Integer.parseInt( props.getProperty( "blending_range_x" ) ) );
			params.setBlendingRangeY( Integer.parseInt( props.getProperty( "blending_range_y" ) ) );
			params.setBlendingRangeZ( Integer.parseInt( props.getProperty( "blending_range_z" ) ) );
		}

		final String iteration = props.getProperty( "iteration_type" );

		if( iteration != null )
		{
			params.setIterationType( LRFFT.PSFTYPE.valueOf( iteration ) );
		}

		params.setJustShowWeights( Boolean.parseBoolean( props.getProperty( "just_show_weights", "false" ) ) );

		params.setNumOfIteration( Integer.parseInt( props.getProperty( "num_of_iteration", "5" ) ) );

		params.setUseTikhonovRegularization( Boolean.parseBoolean( props.getProperty( "use_tikhonov_regularization", "true" ) ) );

		params.setLambda( Double.parseDouble( props.getProperty( "lambda", "0.006" ) ) );



		return params;
	}

	@Override public void process( String[] args )
	{
		process( getParams( args ) );
	}

	public static void main( String[] args )
	{
		// Test mvn commamnd
		//
		// module load cuda/6.5.14
		// export MAVEN_OPTS="-Xms4g -Xmx16g -Djava.awt.headless=true"
		// mvn exec:java -Dexec.mainClass="task.FusionTask" -Dexec.args="-Dxml_filename=/projects/pilot_spim/moon/test.xml -Dmethod=EfficientBayesianBased -Dcompute_on=1 -Dfourier_convolution_cuda_lib=lib/libFourierConvolutionCUDALib.so -Dblock_size='{256, 256, 256}' -Diteration_type=OPTIMIZATION_I -Dmin='{183, 45, -690}' -Dmax='{910, 1926, 714}' -Dexport=Save3dTIFF"
		FusionTask task = new FusionTask();
		task.process( args );
		System.exit( 0 );
	}
}
