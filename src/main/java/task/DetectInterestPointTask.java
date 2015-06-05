package task;

import com.sun.jna.Native;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spim.fiji.plugin.interestpointdetection.DifferenceOf;
import spim.fiji.plugin.interestpointdetection.DifferenceOfGaussian;
import spim.fiji.plugin.interestpointdetection.DifferenceOfMean;
import spim.fiji.plugin.queryXML.HeadlessParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDASeparableConvolution;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Headless module for DetectinterestPointTask
 */
public class DetectInterestPointTask extends AbstractTask
{
	// Algorithms:
	//
	// DifferenceOfMean
	// DifferenceOfGaussian

	private static final Logger LOG = LoggerFactory.getLogger( DetectInterestPointTask.class );

	public String getTitle() { return "Detect Interest Points Task"; }

	public static enum Method { DifferenceOfMean, DifferenceOfGaussian };

	public static class Parameters extends AbstractTask.Parameters
	{
		private Method method;
		private boolean useCluster;

		// Common Parameters
		private double imageSigmaX, imageSigmaY, imageSigmaZ;
		private double additionalSigmaX, additionalSigmaY, additionalSigmaZ;
		private double minIntensity, maxIntensity;
		// localization:
		// 0:"None",
		// 1:"3-dimensional quadratic fit",
		// 2:"Gaussian mask localization fit"
		private int localization, downsampleXY, downsampleZ;

		// Common Advanced Parameters
		private double[] threshold;
		private boolean[] findMin;
		private boolean[] findMax;

		// DifferenceOfMean
		private int[] radius1;
		private int[] radius2;

		// DifferenceOfGaussian
		private double[] sigma;
		// computeOn:
		// 0:"CPU (Java)",
		// 1:"GPU approximate (Nvidia CUDA via JNA)",
		// 2:"GPU accurate (Nvidia CUDA via JNA)"
		private int computeOn;
		private String separableConvolutionCUDALib;

		public Method getMethod()
		{
			return method;
		}

		public void setMethod( Method method )
		{
			this.method = method;
		}

		public boolean isUseCluster()
		{
			return useCluster;
		}

		public void setUseCluster( boolean useCluster )
		{
			this.useCluster = useCluster;
		}

		public double getImageSigmaX()
		{
			return imageSigmaX;
		}

		public void setImageSigmaX( double imageSigmaX )
		{
			this.imageSigmaX = imageSigmaX;
		}

		public double getImageSigmaY()
		{
			return imageSigmaY;
		}

		public void setImageSigmaY( double imageSigmaY )
		{
			this.imageSigmaY = imageSigmaY;
		}

		public double getImageSigmaZ()
		{
			return imageSigmaZ;
		}

		public void setImageSigmaZ( double imageSigmaZ )
		{
			this.imageSigmaZ = imageSigmaZ;
		}

		public double getAdditionalSigmaX()
		{
			return additionalSigmaX;
		}

		public void setAdditionalSigmaX( double additionalSigmaX )
		{
			this.additionalSigmaX = additionalSigmaX;
		}

		public double getAdditionalSigmaY()
		{
			return additionalSigmaY;
		}

		public void setAdditionalSigmaY( double additionalSigmaY )
		{
			this.additionalSigmaY = additionalSigmaY;
		}

		public double getAdditionalSigmaZ()
		{
			return additionalSigmaZ;
		}

		public void setAdditionalSigmaZ( double additionalSigmaZ )
		{
			this.additionalSigmaZ = additionalSigmaZ;
		}

		public double getMinIntensity()
		{
			return minIntensity;
		}

		public void setMinIntensity( double minIntensity )
		{
			this.minIntensity = minIntensity;
		}

		public double getMaxIntensity()
		{
			return maxIntensity;
		}

		public void setMaxIntensity( double maxIntensity )
		{
			this.maxIntensity = maxIntensity;
		}

		public int getLocalization()
		{
			return localization;
		}

		public void setLocalization( int localization )
		{
			this.localization = localization;
		}

		public int getDownsampleXY()
		{
			return downsampleXY;
		}

		public void setDownsampleXY( int downsampleXY )
		{
			this.downsampleXY = downsampleXY;
		}

		public int getDownsampleZ()
		{
			return downsampleZ;
		}

		public void setDownsampleZ( int downsampleZ )
		{
			this.downsampleZ = downsampleZ;
		}

		public double[] getThreshold()
		{
			return threshold;
		}

		public void setThreshold( double[] threshold )
		{
			this.threshold = threshold;
		}

		public boolean[] getFindMin()
		{
			return findMin;
		}

		public void setFindMin( boolean[] findMin )
		{
			this.findMin = findMin;
		}

		public boolean[] getFindMax()
		{
			return findMax;
		}

		public void setFindMax( boolean[] findMax )
		{
			this.findMax = findMax;
		}

		public int[] getRadius1()
		{
			return radius1;
		}

		public void setRadius1( int[] radius1 )
		{
			this.radius1 = radius1;
		}

		public int[] getRadius2()
		{
			return radius2;
		}

		public void setRadius2( int[] radius2 )
		{
			this.radius2 = radius2;
		}

		public double[] getSigma()
		{
			return sigma;
		}

		public void setSigma( double[] sigma )
		{
			this.sigma = sigma;
		}

		public int getComputeOn()
		{
			return computeOn;
		}

		public void setComputeOn( int computeOn )
		{
			this.computeOn = computeOn;
		}

		public String getSeparableConvolutionCUDALib()
		{
			return separableConvolutionCUDALib;
		}

		public void setSeparableConvolutionCUDALib( String separableConvolutionCUDALib )
		{
			this.separableConvolutionCUDALib = separableConvolutionCUDALib;
		}
	}

	public void process( final Parameters params )
	{
		if( params.getMethod() != null )
		{
			switch( params.getMethod() )
			{
				case DifferenceOfGaussian:
					processGaussian( params );
					break;
				case DifferenceOfMean:
					processMean( params );
					break;
			}
		}
	}

	private void processMean( final Parameters params )
	{
		final HeadlessParseQueryXML result = new HeadlessParseQueryXML();
		result.loadXML( params.getXmlFilename(), params.isUseCluster() );

		spimData = result.getData();

		final List< ViewId > viewIdsToProcess = SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		final DifferenceOfMean differenceOfMean = new DifferenceOfMean( result.getData(), viewIdsToProcess);

		final ArrayList< Channel > channels = SpimData2.getAllChannelsSorted( spimData, viewIdsToProcess );

		differenceOfMean.init( channels.size() );

		if ( DifferenceOfMean.defaultBrightness == null || DifferenceOfMean.defaultBrightness.length != channels.size() )
		{
			DifferenceOfMean.defaultBrightness = new int[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				DifferenceOfMean.defaultBrightness[ i ] = 1;
		}

		differenceOfMean.setLocalization( DifferenceOfGaussian.defaultLocalization );

		final ArrayList< Channel > channelsToProcess = differenceOfMean.getChannelsToProcess();
		final int[] brightness = new int[ channelsToProcess.size() ];

		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			final Channel channel = channelsToProcess.get( c );
			brightness[ c ] = DifferenceOfMean.defaultBrightness[ channel.getId() ];
		}

		differenceOfMean.setDownsampleXY( 1 );
		differenceOfMean.setDownsampleZ( 1 );
		differenceOfMean.setAdditionalSigmaX( 0.0 );
		differenceOfMean.setAdditionalSigmaY( 0.0 );
		differenceOfMean.setAdditionalSigmaZ( 0.0 );
		differenceOfMean.setMinIntensity( Double.NaN );
		differenceOfMean.setMaxIntensity( Double.NaN );

		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			final Channel channel = channelsToProcess.get( c );

			if ( brightness[ c ] <= 3 )
			{
				if ( !differenceOfMean.setDefaultValues( channel, brightness[ c ] ) )
					return;
			}
			// TODO: setAdvanceValues and setInteractiveValues are commented out for now
			//			else if ( brightness[ c ] == 4 )
			//			{
			//				if ( !setAdvancedValues( channel ) )
			//					return;
			//			}
			//			else
			//			{
			//				if ( !setInteractiveValues( channel ) )
			//					return;
			//			}
		}

		differenceOfMean.setImageSigmaX( 0.5 );
		differenceOfMean.setImageSigmaY( 0.5 );
		differenceOfMean.setImageSigmaZ( 0.5 );

		findInterestPoints( differenceOfMean, params, spimData, viewIdsToProcess, result.getClusterExtension() );
	}

	private void processGaussian( final Parameters params )
	{
		final HeadlessParseQueryXML result = new HeadlessParseQueryXML();
		result.loadXML( params.getXmlFilename(), params.isUseCluster() );

		spimData = result.getData();

		final List< ViewId > viewIdsToProcess = SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		final DifferenceOfGaussian differenceOfGaussian = new DifferenceOfGaussian( result.getData(), viewIdsToProcess);

		final ArrayList< Channel > channels = SpimData2.getAllChannelsSorted( spimData, viewIdsToProcess );

		differenceOfGaussian.init( channels.size() );

		if ( DifferenceOfGaussian.defaultBrightness == null || DifferenceOfGaussian.defaultBrightness.length != channels.size() )
		{
			DifferenceOfGaussian.defaultBrightness = new int[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				DifferenceOfGaussian.defaultBrightness[ i ] = 1;
		}

		differenceOfGaussian.setLocalization( DifferenceOfGaussian.defaultLocalization );

		final ArrayList< Channel > channelsToProcess = differenceOfGaussian.getChannelsToProcess();
		final int[] brightness = new int[ channelsToProcess.size() ];

		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			final Channel channel = channelsToProcess.get( c );
			brightness[ c ] = DifferenceOfGaussian.defaultBrightness[ channel.getId() ];
		}

		differenceOfGaussian.setDownsampleXY( 1 );
		differenceOfGaussian.setDownsampleZ( 1 );
		differenceOfGaussian.setAdditionalSigmaX( 0.0 );
		differenceOfGaussian.setAdditionalSigmaY( 0.0 );
		differenceOfGaussian.setAdditionalSigmaZ( 0.0 );
		differenceOfGaussian.setMinIntensity( Double.NaN );
		differenceOfGaussian.setMaxIntensity( Double.NaN );

		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			final Channel channel = channelsToProcess.get( c );

			if ( brightness[ c ] <= 3 )
			{
				if ( !differenceOfGaussian.setDefaultValues( channel, brightness[ c ] ) )
					return;
			}
			// TODO: setAdvanceValues and setInteractiveValues are commented out for now
			//			else if ( brightness[ c ] == 4 )
			//			{
			//				if ( !setAdvancedValues( channel ) )
			//					return;
			//			}
			//			else
			//			{
			//				if ( !setInteractiveValues( channel ) )
			//					return;
			//			}
		}

		differenceOfGaussian.setImageSigmaX( 0.5 );
		differenceOfGaussian.setImageSigmaY( 0.5 );
		differenceOfGaussian.setImageSigmaZ( 0.5 );

		if(params.getComputeOn() > 0)
		{
			CUDASeparableConvolution cuda = (CUDASeparableConvolution) Native.loadLibrary( params.getSeparableConvolutionCUDALib(), CUDASeparableConvolution.class );

			if ( cuda == null )
			{
				LOG.info( "Cannot load CUDA JNA library." );
				return;
			}
			else
			{
				ArrayList< CUDADevice > deviceList = new ArrayList< CUDADevice >();
				final int numDevices = cuda.getNumDevicesCUDA();
				if ( numDevices == -1 )
				{
					LOG.info( "Querying CUDA devices crashed, no devices available." );
					return;
				}
				else if ( numDevices == 0 )
				{
					LOG.info( "No CUDA devices detected." );
					return;
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
					catch (UnsatisfiedLinkError e )
					{
						LOG.info( "Using an outdated version of the CUDA libs, cannot query free memory. Assuming total memory." );
						freeMem = mem;
					}

					final int majorVersion = cuda.getCUDAcomputeCapabilityMajorVersion( 0 );
					final int minorVersion = cuda.getCUDAcomputeCapabilityMinorVersion( 0 );

					if(isDebug)
					{
						LOG.info("GPU :" + deviceName);
						LOG.info("Memory :" + freeMem);
					}

					deviceList.add( new CUDADevice( 0, deviceName, mem, freeMem, majorVersion, minorVersion ) );

					differenceOfGaussian.setCuda( cuda );
					differenceOfGaussian.setDeviceList( deviceList );
				}
			}
		}

		DifferenceOfGaussian.defaultComputationChoiceIndex = params.getComputeOn();

		findInterestPoints( differenceOfGaussian, params, spimData, viewIdsToProcess, result.getClusterExtension() );
	}

	private void findInterestPoints(final DifferenceOf ipd, final Parameters params, final SpimData2 data, final List< ViewId > viewIds, final String clusterExtention)
	{
		final String label = "beads";

		// now extract all the detections
		for ( final TimePoint tp : SpimData2.getAllTimePointsSorted( data, viewIds ) )
		{
			final HashMap< ViewId, List< InterestPoint > > points = ipd.findInterestPoints( tp );
			if ( ipd instanceof DifferenceOf )
			{
				LOG.info( "Opening of files took: " + ipd.getBenchmark().openFiles/1000 + " sec." );
				LOG.info( "Detecting interest points took: " + ipd.getBenchmark().computation / 1000 + " sec." );
			}
			// save the file and the path in the XML
			final SequenceDescription seqDesc = data.getSequenceDescription();
			for ( final ViewId viewId : points.keySet() )
			{
				final ViewDescription viewDesc = seqDesc.getViewDescription( viewId.getTimePointId(), viewId.getViewSetupId() );
				final int channelId = viewDesc.getViewSetup().getChannel().getId();
				final InterestPointList list = new InterestPointList(
						data.getBasePath(),
						new File( "interestpoints", "tpId_" + viewId.getTimePointId() + "_viewSetupId_" + viewId.getViewSetupId() + "." + label ) );
				list.setParameters( ipd.getParameters( channelId ) );
				list.setInterestPoints( points.get( viewId ) );

				if ( !list.saveInterestPoints() )
				{
					LOG.info( "Error saving interest point list: " + new File( list.getBaseDir(), list.getFile().toString() + list.getInterestPointsExt() ) );
					return;
				}
				list.setCorrespondingInterestPoints( new ArrayList< CorrespondingInterestPoints >() );
				if ( !list.saveCorrespondingInterestPoints() )
					LOG.info( "Failed to clear corresponding interest point list: " + new File( list.getBaseDir(), list.getFile().toString() + list.getCorrespondencesExt() ) );

				final ViewInterestPointLists vipl = data.getViewInterestPoints().getViewInterestPointLists( viewId );
				vipl.addInterestPointList( label, list );
			}
			// update metadata if necessary
			if ( data.getSequenceDescription().getImgLoader() instanceof AbstractImgLoader )
			{
				LOG.info( "(" + new Date( System.currentTimeMillis() ) + "): Updating metadata ... " );
				try
				{
					( (AbstractImgLoader)data.getSequenceDescription().getImgLoader() ).updateXMLMetaData( data, false );
				}
				catch( Exception e )
				{
					LOG.info( "Failed to update metadata, this should not happen: " + e );
				}
			}

			if( params.isUseCluster() )
				SpimData2.saveXML( data, params.getXmlFilename(), clusterExtention );
			else
				SpimData2.saveXML( data, params.getXmlFilename(), "" );
		}
	}

	private Parameters getParams( final String[] args )
	{
		final Properties props = parseArgument( "DetectInterestPoint", getTitle(), args );

		final Parameters params = new Parameters();
		params.setXmlFilename( props.getProperty( "xml_filename" ) );

		// downsample { 1, 2, 4, 8 }
		params.setDownsampleXY( Integer.parseInt( props.getProperty( "downsample_xy", "1" ) ) );
		params.setDownsampleZ( Integer.parseInt( props.getProperty( "downsample_z", "1" ) ) );

		// additional Smoothing
		params.setAdditionalSigmaX( Double.parseDouble( props.getProperty( "presmooth_sigma_x", "0.0" ) ) );
		params.setAdditionalSigmaY( Double.parseDouble( props.getProperty( "presmooth_sigma_y", "0.0" ) ) );
		params.setAdditionalSigmaZ( Double.parseDouble( props.getProperty( "presmooth_sigma_z", "0.0" ) ) );

		// set min max
		params.setMinIntensity( Double.parseDouble( props.getProperty( "minimal_intensity", "0.0" ) ) );
		params.setMinIntensity( Double.parseDouble( props.getProperty( "maximal_intensity", "65535.0" ) ) );

		// define anisotropy
		params.setImageSigmaX( Double.parseDouble( props.getProperty( "image_sigma_x", "0.5" ) ) );
		params.setImageSigmaY( Double.parseDouble( props.getProperty( "image_sigma_y", "0.5" ) ) );
		params.setImageSigmaZ( Double.parseDouble( props.getProperty( "image_sigma_z", "0.5" ) ) );

		// sub-pixel localization
		params.setLocalization( Integer.parseInt( props.getProperty( "subpixel_localization", "1" ) ) );


		final String method = props.getProperty( "method" );

		if( method.equals( "DifferenceOfMean" ) )
		{
			params.setMethod( Method.DifferenceOfMean );

			// The below is for advanced parameters

//			// -Dradius_1={2, 2, 2}
//			params.setRadius1( PluginHelper.parseArrayIntegerString( props.getProperty( "radius_1" ) ) );
//			// -Dradius_2={3, 3, 3}
//			params.setRadius2( PluginHelper.parseArrayIntegerString( props.getProperty( "radius_2" ) ) );
//			// -Dthreshold={0.02, 0.02, 0.02}
//			params.setThreshold( PluginHelper.parseArrayDoubleString( props.getProperty( "threshold" ) ) );
//			// -Dfind_minima={false, false, false}
//			params.setFindMin( PluginHelper.parseArrayBooleanString( props.getProperty( "find_minima" ) ) );
//			// -Dfind_maxima={true, true, true}
//			params.setFindMax( PluginHelper.parseArrayBooleanString( props.getProperty( "find_maxima" ) ) );
		}
		else if( method.equals( "DifferenceOfGaussian" ) )
		{
			params.setMethod( Method.DifferenceOfGaussian );

			// The below is for advanced parameters

			params.setComputeOn( Integer.parseInt( props.getProperty( "compute_on", "0" ) ) );
			params.setSeparableConvolutionCUDALib( props.getProperty( "separable_convolution_cuda_lib" ) );

//			// -Dsigma={1.8, 1.8, 1.8}
//			params.setSigma( PluginHelper.parseArrayDoubleString( props.getProperty( "sigma" ) ) );
//			// -Dthreshold={0.02, 0.02, 0.02}
//			params.setThreshold( PluginHelper.parseArrayDoubleString( props.getProperty( "threshold" ) ) );
//			// -Dfind_minima={false, false, false}
//			params.setFindMin( PluginHelper.parseArrayBooleanString( props.getProperty( "find_minima" ) ) );
//			// -Dfind_maxima={true, true, true}
//			params.setFindMax( PluginHelper.parseArrayBooleanString( props.getProperty( "find_maxima" ) ) );
		}

		return params;
	}

	@Override public void process( final String[] args )
	{
		process( getParams( args ) );
	}

	public static void main( String[] argv )
	{
		// Test mvn commamnd
		//
		// module load cuda/6.5.14
		// export MAVEN_OPTS="-Xms4g -Xmx16g -Djava.awt.headless=true"
		// mvn exec:java -Dexec.mainClass="task.DetectInterestPointTask" -Dexec.args="-Dxml_filename=/projects/pilot_spim/moon/test.xml -Dmethod=DifferenceOfGaussian -Dcompute_on=1 -Dseparable_convolution_cuda_lib=lib/libSeparableConvolutionCUDALib.so"
		DetectInterestPointTask task = new DetectInterestPointTask();
		task.process( argv );
		System.exit( 0 );
	}
}
