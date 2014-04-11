package spim.fiji.plugin.interestpointdetection;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.SpimData2;

public abstract class DifferenceOf extends InterestPointDetection
{	
	/**
	 * Can be used to manually set min (minmaxset[0]) and max (minmaxset[1]) value for all views
	 * that are opened. Otherwise min and max will be read from the images 
	 */
	public static float[] minmaxset = null;

	public static String[] localizationChoice = { "None", "3-dimensional quadratic fit", "Gaussian mask localization fit" };	
	public static String[] brightnessChoice = { "Very weak & small (beads)", "Weak & small (beads)", "Comparable to Sample & small (beads)", "Strong & small (beads)", "Advanced ...", "Interactive ..." };
	
	public static int defaultLocalization = 1;
	public static int[] defaultBrightness = null;
	
	public static int defaultTimepointChoice = 0;
	public static int defaultAngleChoice = 0;
	public static int defaultIlluminationChoice = 0;
		
	protected int localization;

	public DifferenceOf(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess)
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public boolean queryParameters()
	{		
		final ArrayList< Channel > channels = spimData.getSequenceDescription().getAllChannels();

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

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.localization = defaultLocalization = gd.getNextChoiceIndex();

		for ( int c = 0; c < channelsToProcess.size(); ++c )
		{
			final Channel channel = channelsToProcess.get( c );
			final int brightness = defaultBrightness[ channel.getId() ] = gd.getNextChoiceIndex();
			
			if ( brightness <= 3 )
			{
				if ( !setDefaultValues( channel, brightness ) )
					return false;
			}
			else if ( brightness == 4 )
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
		
		return true;
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
		
		final ArrayList< Angle > angles = spimData.getSequenceDescription().getAllAngles();
		final String[] angleNames = new String[ angles.size() ];
		for ( int i = 0; i < angles.size(); ++i )
			angleNames[ i ] = angles.get( i ).getName();
		
		final ArrayList< Illumination > illuminations = spimData.getSequenceDescription().getAllIlluminations();
		final String[] illuminationNames = new String[ illuminations.size() ];
		for ( int i = 0; i < illuminations.size(); ++i )
			illuminationNames[ i ] = illuminations.get( i ).getName();
		
		gd.addMessage( text );
		gd.addChoice( "Timepoint", timepointNames, timepointNames[ defaultTimepointChoice ] );
		gd.addChoice( "Angle", angleNames, angleNames[ defaultAngleChoice ] );
		gd.addChoice( "Illumination", illuminationNames, illuminationNames[ defaultIlluminationChoice ] );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;
		
		final TimePoint tp = timepointsToProcess.get( defaultTimepointChoice = gd.getNextChoiceIndex() );
		final Angle angle = angles.get( defaultAngleChoice = gd.getNextChoiceIndex() );
		final Illumination illumination = illuminations.get( defaultIlluminationChoice = gd.getNextChoiceIndex() );
		
		final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), tp, channel, angle, illumination );

		if ( viewId == null )
			IOFunctions.println( "An error occured. Count not find the corresponding ViewSetup for angle: " + angle.getName() + " channel: " + channel.getName() + " illum: " + illumination.getName() + " timepoint: " + tp.getName() );
		
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
}
