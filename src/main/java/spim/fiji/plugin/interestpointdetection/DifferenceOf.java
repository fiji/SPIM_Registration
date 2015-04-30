package spim.fiji.plugin.interestpointdetection;

import ij.gui.GenericDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.*;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import spim.fiji.plugin.Toggle_Cluster_Options;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.process.interestpointdetection.Downsample;

public abstract class DifferenceOf extends InterestPointDetection
{
	protected static final int[] ds = { 1, 2, 4, 8 };

	public static String[] downsampleChoiceXY = { ds[ 0 ] + "x", ds[ 1 ] + "x", ds[ 2 ] + "x", ds[ 3 ] + "x", "Match Z Resolution (less downsampling)", "Match Z Resolution (more downsampling)"  };
	public static String[] downsampleChoiceZ = { ds[ 0 ] + "x", ds[ 1 ] + "x", ds[ 2 ] + "x", ds[ 3 ] + "x" };
	public static String[] localizationChoice = { "None", "3-dimensional quadratic fit", "Gaussian mask localization fit" };	
	public static String[] brightnessChoice = { "Very weak & small (beads)", "Weak & small (beads)", "Comparable to Sample & small (beads)", "Strong & small (beads)", "Advanced ...", "Interactive ..." };
	
	public static int defaultDownsampleXYIndex = 4;
	public static int defaultDownsampleZIndex = 0;

	public static int defaultLocalization = 1;
	public static int[] defaultBrightness = null;

	public static double defaultImageSigmaX = 0.5;
	public static double defaultImageSigmaY = 0.5;
	public static double defaultImageSigmaZ = 0.5;

	public static int defaultViewChoice = 0;

	public static double defaultAdditionalSigmaX = 0.0;
	public static double defaultAdditionalSigmaY = 0.0;
	public static double defaultAdditionalSigmaZ = 0.0;

	public static double defaultMinIntensity = 0.0;
	public static double defaultMaxIntensity = 65535.0;
	
	protected double imageSigmaX, imageSigmaY, imageSigmaZ;
	protected double additionalSigmaX, additionalSigmaY, additionalSigmaZ;
	protected double minIntensity, maxIntensity;

	// downsampleXY == 0 : a bit less then z-resolution
	// downsampleXY == -1 : a bit more then z-resolution
	protected int localization, downsampleXY, downsampleZ;

	final ArrayList< Channel > channelsToProcess;

	public DifferenceOf( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );

		if ( viewIdsToProcess != null )
			this.channelsToProcess = SpimData2.getAllChannelsSorted( spimData, viewIdsToProcess );
		else
			this.channelsToProcess = null;
	}

	protected abstract void addAddtionalParameters( final GenericDialog gd );
	protected abstract boolean queryAdditionalParameters( final GenericDialog gd );
	
	@Override
	public boolean queryParameters( final boolean downsample, final boolean defineAnisotropy, final boolean additionalSmoothing, final boolean setMinMax )
	{
		final List< Channel > channels = spimData.getSequenceDescription().getAllChannelsOrdered();

		// tell the implementing classes the total number of channels
		init( channels.size() );
		
		final GenericDialog gd = new GenericDialog( getDescription() );
		gd.addChoice( "Subpixel_localization", localizationChoice, localizationChoice[ defaultLocalization ] );
		
		// there are as many channel presets as there are total channels
		if ( defaultBrightness == null || defaultBrightness.length != channels.size() )
		{
			defaultBrightness = new int[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				defaultBrightness[ i ] = 1;
		}

		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			if ( channelsToProcess.size() == 1 )
				gd.addChoice( "Interest_point_specification (channel_" + channelsToProcess.get( c ).getName() + ")", brightnessChoice, brightnessChoice[ defaultBrightness[ channelsToProcess.get( c ).getId() ] ] );
			else
				gd.addChoice( "Interest_point_specification_(channel_" + channelsToProcess.get( c ).getName().replace( " ", "_" ) + ")", brightnessChoice, brightnessChoice[ defaultBrightness[ channelsToProcess.get( c ).getId() ] ] );
		}

		if ( downsample )
		{
			gd.addChoice( "Downsample_XY", downsampleChoiceXY, downsampleChoiceXY[ defaultDownsampleXYIndex ] );
			gd.addChoice( "Downsample_Z", downsampleChoiceZ, downsampleChoiceZ[ defaultDownsampleZIndex ] );
		}

		if ( additionalSmoothing )
		{
			gd.addNumericField( "Presmooth_Sigma_X", defaultAdditionalSigmaX, 5 );
			gd.addNumericField( "Presmooth_Sigma_Y", defaultAdditionalSigmaY, 5 );
			gd.addNumericField( "Presmooth_Sigma_Z", defaultAdditionalSigmaZ, 5 );			

			gd.addMessage( "Note: a sigma of 0.0 means no additional smoothing.", GUIHelper.mediumstatusfont );
		}

		if ( setMinMax )
		{
			gd.addNumericField( "Minimal_intensity", defaultMinIntensity, 1 );
			gd.addNumericField( "Maximal_intensity", defaultMaxIntensity, 1 );
		}

		if ( defineAnisotropy )
		{
			gd.addNumericField( "Image_Sigma_X", defaultImageSigmaX, 5 );
			gd.addNumericField( "Image_Sigma_Y", defaultImageSigmaY, 5 );
			gd.addNumericField( "Image_Sigma_Z", defaultImageSigmaZ, 5 );
			
			gd.addMessage( "Please consider that usually the lower resolution in z is compensated by a lower sampling rate in z.\n" +
					"Only adjust the initial sigma's if this is not the case.", GUIHelper.mediumstatusfont );
		}

		addAddtionalParameters( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.localization = defaultLocalization = gd.getNextChoiceIndex();

		final int[] brightness = new int[ channelsToProcess.size() ];
		
		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			final Channel channel = channelsToProcess.get( c );
			brightness[ c ] = defaultBrightness[ channel.getId() ] = gd.getNextChoiceIndex();			
		}

		if ( downsample )
		{
			int dsxy = defaultDownsampleXYIndex = gd.getNextChoiceIndex();
			int dsz = defaultDownsampleZIndex = gd.getNextChoiceIndex();

			if ( dsz == 0 )
				downsampleZ = 1;
			else if ( dsz == 1 )
				downsampleZ = 2;
			else if ( dsz == 2 )
				downsampleZ = 4;
			else
				downsampleZ = 8;

			if ( dsxy == 0 )
				downsampleXY = 1;
			else if ( dsxy == 1 )
				downsampleXY = 2;
			else if ( dsxy == 2 )
				downsampleXY = 4;
			else if ( dsxy == 3 )
				downsampleXY = 8;
			else if ( dsxy == 4 )
				downsampleXY = 0;
			else
				downsampleXY = -1;
		}
		else
		{
			downsampleXY = downsampleZ = 1;
		}

		if ( additionalSmoothing )
		{
			additionalSigmaX = defaultAdditionalSigmaX = gd.getNextNumber();
			additionalSigmaY = defaultAdditionalSigmaY = gd.getNextNumber();
			additionalSigmaZ = defaultAdditionalSigmaZ = gd.getNextNumber();
		}
		else
		{
			additionalSigmaX = additionalSigmaY = additionalSigmaZ = 0.0;
		}
		
		if ( setMinMax )
		{
			minIntensity = defaultMinIntensity = gd.getNextNumber();
			maxIntensity = defaultMaxIntensity = gd.getNextNumber();
		}
		else
		{
			minIntensity = maxIntensity = Double.NaN;
		}

		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			final Channel channel = channelsToProcess.get( c );
			
			if ( brightness[ c ] <= 3 )
			{
				if ( !setDefaultValues( channel, brightness[ c ] ) )
					return false;
			}
			else if ( brightness[ c ] == 4 )
			{
				if ( !setAdvancedValues( channel ) )
					return false;
			}
			else
			{
				if ( !setInteractiveValues( channel ) )
					return false;
			}
		}

		if ( defineAnisotropy )
		{
			imageSigmaX = defaultImageSigmaX = gd.getNextNumber();
			imageSigmaY = defaultImageSigmaY = gd.getNextNumber();
			imageSigmaZ = defaultImageSigmaZ = gd.getNextNumber();
		}
		else
		{
			imageSigmaX = imageSigmaY = imageSigmaZ = 0.5;
		}

		if ( !queryAdditionalParameters( gd ) )
			return false;
		else
			return true;
	}
	
	protected < T extends RealType< T > > void preSmooth( final RandomAccessibleInterval< T > img )
	{
		if ( additionalSigmaX > 0.0 || additionalSigmaY > 0.0 || additionalSigmaZ > 0.0 )
		{
			IOFunctions.println( "presmoothing image with sigma=[" + additionalSigmaX + "," + additionalSigmaY + "," + additionalSigmaZ + "]" );
			try
			{
				Gauss3.gauss( new double[]{ additionalSigmaX, additionalSigmaY, additionalSigmaZ }, Views.extendMirrorSingle( img ), img );
			}
			catch (IncompatibleTypeException e)
			{
				IOFunctions.println( "presmoothing failed: " + e );
				e.printStackTrace();
			}
		}
	}

	/**
	 * Figure out which view to use for the interactive preview
	 * 
	 * @param dialogHeader
	 * @param text
	 * @param channel
	 * @return
	 */
	protected ViewId getViewSelection( final String dialogHeader, final String text, final Channel channel )
	{
		final ArrayList< ViewDescription > views = SpimData2.getAllViewIdsForChannelSorted( spimData, viewIdsToProcess, channel );
		final String[] viewChoice = new String[ views.size() ];

		for ( int i = 0; i < views.size(); ++i )
		{
			final ViewDescription vd = views.get( i );
			viewChoice[ i ] = "Timepoint " + vd.getTimePointId() + ", Angle " + vd.getViewSetup().getAngle().getName() + ", Illum " + vd.getViewSetup().getIllumination().getName() + ", ViewSetupId " + vd.getViewSetupId();
		}

		if ( defaultViewChoice >= views.size() )
			defaultViewChoice = 0;

		final GenericDialog gd = new GenericDialog( dialogHeader );

		gd.addMessage( text );
		gd.addChoice( "View", viewChoice, viewChoice[ defaultViewChoice ] );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		final ViewId viewId = views.get( defaultViewChoice = gd.getNextChoiceIndex() );

		return viewId;
	}

	/**
	 * This is only necessary to make static objects so that the ImageJ dialog remembers choices
	 * for the right channel
	 * 
	 * @param numChannels - the TOTAL number of channels (not only the ones to process)
	 */
	protected abstract void init( final int numChannels );

	protected abstract boolean setDefaultValues( final Channel channel, final int brightness );
	protected abstract boolean setAdvancedValues( final Channel channel );
	protected abstract boolean setInteractiveValues( final Channel channel );

	protected void correctForDownsampling( final List< InterestPoint > ips, final AffineTransform3D t )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Correcting coordinates for downsampling (xy=" + downsampleXY + "x, z=" + downsampleZ + "x) using AffineTransform: " + t );

		final double[] tmp = new double[ ips.get( 0 ).getL().length ];

		for ( final InterestPoint ip : ips )
		{
			t.apply( ip.getL(), tmp );

			ip.getL()[ 0 ] = tmp[ 0 ];
			ip.getL()[ 1 ] = tmp[ 1 ];
			ip.getL()[ 2 ] = tmp[ 2 ];

			t.apply( ip.getW(), tmp );

			ip.getW()[ 0 ] = tmp[ 0 ];
			ip.getW()[ 1 ] = tmp[ 1 ];
			ip.getW()[ 2 ] = tmp[ 2 ];
		}
	}

	public int downsampleFactor( final int downsampleXY, final int downsampleZ, final VoxelDimensions v )
	{
		final double calXY = Math.min( v.dimension( 0 ), v.dimension( 1 ) );
		final double calZ = v.dimension( 2 ) * downsampleZ;
		final double log2ratio = Math.log( calZ / calXY ) / Math.log( 2 );

		final double exp2;

		if ( downsampleXY == 0 )
			exp2 = Math.pow( 2, Math.floor( log2ratio ) );
		else
			exp2 = Math.pow( 2, Math.ceil( log2ratio ) );

		return (int)Math.round( exp2 );
	}
	
	protected RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > openAndDownsample(
			final SpimData2 spimData,
			final ViewDescription vd,
			final AffineTransform3D t )
	{
		IOFunctions.println(
				"(" + new Date(System.currentTimeMillis()) + "): "
				+ "Requesting Img from ImgLoader (tp=" + vd.getTimePointId() + ", setup=" + vd.getViewSetupId() + ")" );

		int downsampleXY = this.downsampleXY;

		// downsampleXY == 0 : a bit less then z-resolution
		// downsampleXY == -1 : a bit more then z-resolution
		if ( downsampleXY < 1 )
			this.downsampleXY = downsampleXY = downsampleFactor( downsampleXY, downsampleZ, vd.getViewSetup().getVoxelSize() );

		if ( downsampleXY > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in XY " + downsampleXY + "x ..." );

		if ( downsampleZ > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in Z " + downsampleZ + "x ..." );

		int dsx = downsampleXY;
		int dsy = downsampleXY;
		int dsz = downsampleZ;

		RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > input = null;

		ImgLoader< ? > imgLoader = spimData.getSequenceDescription().getImgLoader();
		if ( Hdf5ImageLoader.class.isInstance( imgLoader ) )
			imgLoader = ( ( Hdf5ImageLoader ) imgLoader ).getMonolithicImageLoader();
		
		if ( ( dsx > 1 || dsy > 1 || dsz > 1 ) && MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			MultiResolutionImgLoader< ? > mrImgLoader = ( MultiResolutionImgLoader< ? > ) imgLoader;

			double[][] mipmapResolutions = mrImgLoader.getMipmapResolutions( vd.getViewSetupId() );
			
			int bestLevel = 0;
			for ( int level = 0; level < mipmapResolutions.length; ++level )
			{
				double[] factors = mipmapResolutions[ level ];
				
				// this fails if factors are not ints
				final int fx = (int)Math.round( factors[ 0 ] );
				final int fy = (int)Math.round( factors[ 1 ] );
				final int fz = (int)Math.round( factors[ 2 ] );
				
				if ( fx <= dsx && fy <= dsy && fz <= dsz && contains( fx, ds ) && contains( fy, ds ) && contains( fz, ds ) )
					bestLevel = level;
			}

			final int fx = (int)Math.round( mipmapResolutions[ bestLevel ][ 0 ] );
			final int fy = (int)Math.round( mipmapResolutions[ bestLevel ][ 1 ] );
			final int fz = (int)Math.round( mipmapResolutions[ bestLevel ][ 2 ] );

			t.set( mrImgLoader.getMipmapTransforms(vd.getViewSetupId())[ bestLevel ] );

			dsx /= fx;
			dsy /= fy;
			dsz /= fz;

			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): " +
					"Using precomputed Multiresolution Images [" + fx + "x" + fy + "x" + fz + "], " +
					"Remaining downsampling [" + dsx + "x" + dsy + "x" + dsz + "]" );

			input = mrImgLoader.getFloatImage( vd, bestLevel, false );
		}
		else
		{
			input = imgLoader.getFloatImage( vd, false );
			t.identity();
		}

		final ImgFactory< net.imglib2.type.numeric.real.FloatType > f = ((Img<net.imglib2.type.numeric.real.FloatType>)input).factory();

		t.set( downsampleXY, 0, 0 );
		t.set( downsampleXY, 1, 1 );
		t.set( downsampleZ, 2, 2 );

		for ( ;dsx > 1; dsx /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ true, false, false } );

		for ( ;dsy > 1; dsy /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, true, false } );

		for ( ;dsz > 1; dsz /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, false, true } );

		return input;
	}

	private static final boolean contains( final int i, final int[] values )
	{
		for ( final int j : values )
			if ( i == j )
				return true;

		return false;
	}

	public void defaultProcess(String xmlFileName, String clusterExtension)
	{
		final String label = "beads";

		ArrayList< Channel > channels = SpimData2.getAllChannelsSorted( spimData, viewIdsToProcess );

		init( channels.size() );

		if ( defaultBrightness == null || defaultBrightness.length != channels.size() )
		{
			defaultBrightness = new int[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				defaultBrightness[ i ] = 1;
		}

		this.localization = defaultLocalization;

		final int[] brightness = new int[ channelsToProcess.size() ];

		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			final Channel channel = channelsToProcess.get( c );
			brightness[ c ] = defaultBrightness[ channel.getId() ];
		}

		downsampleXY = downsampleZ = 1;
		additionalSigmaX = additionalSigmaY = additionalSigmaZ = 0.0;
		minIntensity = maxIntensity = Double.NaN;

		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			final Channel channel = channelsToProcess.get( c );

			if ( brightness[ c ] <= 3 )
			{
				if ( !setDefaultValues( channel, brightness[ c ] ) )
					return;
			}
			else if ( brightness[ c ] == 4 )
			{
				if ( !setAdvancedValues( channel ) )
					return;
			}
			else
			{
				if ( !setInteractiveValues( channel ) )
					return;
			}
		}

		imageSigmaX = imageSigmaY = imageSigmaZ = 0.5;

		SpimData2 data = spimData;
		List< ViewId > viewIds = viewIdsToProcess;
		DifferenceOf ipd = this;

		// now extract all the detections
		for ( final TimePoint tp : SpimData2.getAllTimePointsSorted( data, viewIds ) )
		{
			final HashMap< ViewId, List< InterestPoint > > points = findInterestPoints( tp );
			if ( ipd instanceof DifferenceOf )
			{
				IOFunctions.println( "Opening of files took: " + ((DifferenceOf)ipd).getBenchmark().openFiles/1000 + " sec." );
				IOFunctions.println( "Detecting interest points took: " + ((DifferenceOf)ipd).getBenchmark().computation/1000 + " sec." );
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
					IOFunctions.println( "Error saving interest point list: " + new File( list.getBaseDir(), list.getFile().toString() + list.getInterestPointsExt() ) );
					return;
				}
				list.setCorrespondingInterestPoints( new ArrayList< CorrespondingInterestPoints >() );
				if ( !list.saveCorrespondingInterestPoints() )
					IOFunctions.println( "Failed to clear corresponding interest point list: " + new File( list.getBaseDir(), list.getFile().toString() + list.getCorrespondencesExt() ) );

				final ViewInterestPointLists vipl = data.getViewInterestPoints().getViewInterestPointLists( viewId );
				vipl.addInterestPointList( label, list );
			}
			// update metadata if necessary
			if ( data.getSequenceDescription().getImgLoader() instanceof AbstractImgLoader )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Updating metadata ... " );
				try
				{
					( (AbstractImgLoader)data.getSequenceDescription().getImgLoader() ).updateXMLMetaData( data, false );
				}
				catch( Exception e )
				{
					IOFunctions.println( "Failed to update metadata, this should not happen: " + e );
				}
			}

			if( Toggle_Cluster_Options.displayClusterProcessing )
				SpimData2.saveXML( data, xmlFileName, clusterExtension );
			else
				SpimData2.saveXML( data, xmlFileName, "" );
		}
	}
}
