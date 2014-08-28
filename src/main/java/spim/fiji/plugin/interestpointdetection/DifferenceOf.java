package spim.fiji.plugin.interestpointdetection;

import ij.gui.GenericDialog;

import java.util.Date;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointdetection.Downsample;

public abstract class DifferenceOf extends InterestPointDetection
{
	public static String[] downsampleChoice = { "1x", "2x", "4x", "8x" };
	public static String[] localizationChoice = { "None", "3-dimensional quadratic fit", "Gaussian mask localization fit" };	
	public static String[] brightnessChoice = { "Very weak & small (beads)", "Weak & small (beads)", "Comparable to Sample & small (beads)", "Strong & small (beads)", "Advanced ...", "Interactive ..." };
	
	public static int defaultDownsampleXYIndex = 1;
	public static int defaultDownsampleZIndex = 0;

	public static int defaultLocalization = 1;
	public static int[] defaultBrightness = null;
	
	public static int defaultTimepointChoice = 0;
	public static int defaultAngleChoice = 0;
	public static int defaultIlluminationChoice = 0;
	
	public static double defaultImageSigmaX = 0.5;
	public static double defaultImageSigmaY = 0.5;
	public static double defaultImageSigmaZ = 0.5;

	public static double defaultAdditionalSigmaX = 0.0;
	public static double defaultAdditionalSigmaY = 0.0;
	public static double defaultAdditionalSigmaZ = 0.0;

	public static double defaultMinIntensity = 0.0;
	public static double defaultMaxIntensity = 65535.0;
	
	protected double imageSigmaX, imageSigmaY, imageSigmaZ;
	protected double additionalSigmaX, additionalSigmaY, additionalSigmaZ;
	protected double minIntensity, maxIntensity;
	protected int localization, downsampleXY, downsampleZ;

	public DifferenceOf(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess)
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
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
			gd.addChoice( "Interest_point_specification_(channel_" + channelsToProcess.get( c ).getName() + ")", brightnessChoice, brightnessChoice[ defaultBrightness[ channelsToProcess.get( c ).getId() ] ] );

		if ( downsample )
		{
			gd.addChoice( "Downsample_XY", downsampleChoice, downsampleChoice[ defaultDownsampleXYIndex ] );
			gd.addChoice( "Downsample_Z", downsampleChoice, downsampleChoice[ defaultDownsampleZIndex ] );
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

			if ( dsxy == 0 )
				downsampleXY = 1;
			else if ( dsxy == 1 )
				downsampleXY = 2;
			else if ( dsxy == 2 )
				downsampleXY = 4;
			else
				downsampleXY = 8;

			if ( dsz == 0 )
				downsampleZ = 1;
			else if ( dsz == 1 )
				downsampleZ = 2;
			else if ( dsz == 2 )
				downsampleZ = 4;
			else
				downsampleZ = 8;
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
		final GenericDialog gd = new GenericDialog( dialogHeader );
		
		final String[] timepointNames = new String[ timepointsToProcess.size() ];
		for ( int i = 0; i < timepointNames.length; ++i )
			timepointNames[ i ] = timepointsToProcess.get( i ).getName();
		
		final List< Angle > angles = spimData.getSequenceDescription().getAllAnglesOrdered();
		final String[] angleNames = new String[ angles.size() ];
		for ( int i = 0; i < angles.size(); ++i )
			angleNames[ i ] = angles.get( i ).getName();
		
		final List< Illumination > illuminations = spimData.getSequenceDescription().getAllIlluminationsOrdered();
		final String[] illuminationNames = new String[ illuminations.size() ];
		for ( int i = 0; i < illuminations.size(); ++i )
			illuminationNames[ i ] = illuminations.get( i ).getName();
		
		gd.addMessage( text );
		gd.addChoice( "Timepoint", timepointNames, timepointNames[ defaultTimepointChoice ] );
		gd.addChoice( "Angle", angleNames, angleNames[ defaultAngleChoice ] );
		gd.addChoice( "Illumination", illuminationNames, illuminationNames[ defaultIlluminationChoice ] );

		gd.addMessage( "" );
		gd.addMessage( "Note: Be sure that the view that you select is present at this timepoint!" );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;
		
		final TimePoint tp = timepointsToProcess.get( defaultTimepointChoice = gd.getNextChoiceIndex() );
		final Angle angle = angles.get( defaultAngleChoice = gd.getNextChoiceIndex() );
		final Illumination illumination = illuminations.get( defaultIlluminationChoice = gd.getNextChoiceIndex() );
		
		final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), tp, channel, angle, illumination );
		
		if ( viewId == null )
			IOFunctions.println( "This ViewSetup is not present." );
		
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

	protected void correctForDownsampling( final List< InterestPoint > ips )
	{
		if ( downsampleXY == 1 && downsampleZ == 1 )
			return;

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Correcting coordinates for downsampling" );

		for ( final InterestPoint ip : ips )
		{
			ip.getL()[ 0 ] *= downsampleXY;
			ip.getL()[ 1 ] *= downsampleXY;
			ip.getL()[ 2 ] *= downsampleZ;

			ip.getW()[ 0 ] *= downsampleXY;
			ip.getW()[ 1 ] *= downsampleXY;
			ip.getW()[ 2 ] *= downsampleZ;
		}
	}

	protected RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > downsample( RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > input )
	{
		final ImgFactory< net.imglib2.type.numeric.real.FloatType > f = ((Img<net.imglib2.type.numeric.real.FloatType>)input).factory();

		if ( downsampleXY > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in XY " + downsampleXY + "x ..." );

		for ( int dsx = downsampleXY; dsx > 1; dsx /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ true, false, false } );

		for ( int dsy = downsampleXY; dsy > 1; dsy /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, true, false } );

		if ( downsampleZ > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in Z " + downsampleZ + "x ..." );

		for ( int dsz = downsampleZ; dsz > 1; dsz /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, false, true } );

		return input;
	}


}
