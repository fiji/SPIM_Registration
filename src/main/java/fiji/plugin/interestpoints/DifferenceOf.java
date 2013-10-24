package fiji.plugin.interestpoints;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Channel;

import fiji.spimdata.SpimDataBeads;

public abstract class DifferenceOf implements InterestPointDetection
{
	public static String[] localizationChoice = { "None", "3-dimensional quadratic fit (all detections)", "Gauss fit (true correspondences)", "Gauss fit (all detections)" };	
	public static String[] brightnessChoice = { "Very weak & small (beads)", "Weak & small (beads)", "Comparable to Sample & small (beads)", "Strong & small (beads)", "Advanced ...", "Interactive ..." };
	
	public static int defaultLocalization = 1;
	public static int[] defaultBrightness = null;
	
	protected int localization;
	protected int[] brightness;
	
	@Override
	public boolean queryParameters( final SpimDataBeads spimData, final boolean[] channelIds, final ArrayList< Integer > timepointindices )
	{
		final GenericDialog gd = new GenericDialog( getDescription() );
		gd.addChoice( "Subpixel_localization", localizationChoice, localizationChoice[ defaultLocalization ] );

		final ArrayList< Channel > channels = spimData.getSequenceDescription().getAllChannels();
		
		if ( defaultBrightness == null || defaultBrightness.length != channels.size() )
		{
			defaultBrightness = new int[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				defaultBrightness[ i ] = 1;
		}
		
		for ( int c = 0; c < channelIds.length; ++c )
			if ( channelIds[ c ] )
				gd.addChoice( "Interest_point_specification_(channel_" + channels.get( c ).getName() + ")", brightnessChoice, brightnessChoice[ defaultBrightness[ c ] ] );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.localization = defaultLocalization = gd.getNextChoiceIndex();
		this.brightness = new int[ channels.size() ];
		
		for ( int c = 0; c < channelIds.length; ++c )
			if ( channelIds[ c ] )
				brightness[ c ] = defaultBrightness[ c ] = gd.getNextChoiceIndex();
		
		return true;
	}

}
