package fiji.plugin;

import fiji.plugin.LoadParseQueryXML.XMLParseResult;
import fiji.plugin.interestpoints.DifferenceOfMean;
import fiji.plugin.interestpoints.DifferencOfGaussian;
import fiji.plugin.interestpoints.InterestPointDetection;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.io.IOFunctions;

public class Detect_Interest_Points implements PlugIn
{
	public static ArrayList< InterestPointDetection > staticAlgorithms = new ArrayList< InterestPointDetection >();
	public static int defaultAlgorithm = 0;
	public static boolean[] defaultChannelChoice = null;
	public static String defaultLabel = "beads";
	
	static
	{
		staticAlgorithms.add( new DifferenceOfMean() );
		staticAlgorithms.add( new DifferencOfGaussian() );
	}
	
	@Override
	public void run( final String arg )
	{
		final XMLParseResult result = new LoadParseQueryXML().queryXML( true );
		
		if ( result == null )
			return;
		
		final ArrayList< Channel > channels = result.getData().getSequenceDescription().getAllChannels( );
		final ArrayList< Angle > angles = result.getData().getSequenceDescription().getAllAngles();
		final ArrayList< Illumination > illuminations = result.getData().getSequenceDescription().getAllIlluminations();
		
		final ArrayList< InterestPointDetection > algorithms = new ArrayList< InterestPointDetection >();
		final String[] descriptions = new String[ staticAlgorithms.size() ];
		
		int i = 0;
		for ( final InterestPointDetection ipd : staticAlgorithms )
		{
			algorithms.add( ipd.newInstance() );
			descriptions[ i ] = algorithms.get( i++ ).getDescription();
		}
		
		if ( defaultAlgorithm >= descriptions.length )
			defaultAlgorithm = 0;
		
		final GenericDialog gd = new GenericDialog( "Detect Interest Points" );
		
		gd.addChoice( "Type_of_interest_point_detection", descriptions, descriptions[ defaultAlgorithm ] );
		gd.addStringField( "Label_interest_points", defaultLabel );
		
		if ( channels.size() > 1 )
		{
			if ( defaultChannelChoice == null || defaultChannelChoice.length != channels.size() )
			{
				defaultChannelChoice = new boolean[ channels.size() ];
				for ( i = 0; i < channels.size(); ++i )
					defaultChannelChoice[ i ] = true;
			}
			
			gd.addMessage( "" );
			gd.addMessage( "Choose channels to detect interest points in", GUIHelper.largefont );
			
			for ( i = 0; i < channels.size(); ++i )
				gd.addCheckbox( "Channel_" + channels.get( i ).getName(), defaultChannelChoice[ i ] );
		}
		
		gd.addMessage( "" );
		GUIHelper.addWebsite( gd );
		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return;
		
		final InterestPointDetection ipd = algorithms.get( defaultAlgorithm = gd.getNextChoiceIndex() );
		final String label = defaultLabel = gd.getNextString();
		final boolean[] processChannels = new boolean[ channels.size() ];
		
		if ( channels.size() > 1 )
		{
			int count = 0;
			
			for ( i = 0; i < channels.size(); ++i )
			{
				processChannels[ i ] = defaultChannelChoice[ i ] = gd.getNextBoolean();
				if ( processChannels[ i ] )
					++count;
			}
			
			if ( count == 0 )
			{
				IOFunctions.println( "No channels selected. Quitting." );
				return;
			}
		}
		else
		{
			processChannels[ 0 ] = true;
		}

		// the interest point detection should query its parameters
		ipd.queryParameters( result.getData(), processChannels, result.getTimePointIndicies() );
		
		// now extract all the detections
	}
	
	
	
	public static void main( final String[] args )
	{
		new Detect_Interest_Points().run( null );
	}
}
