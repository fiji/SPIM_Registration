package fiji.plugin;

import fiji.plugin.LoadParseQueryXML.XMLParseResult;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;

public class Detect_Interest_Points implements PlugIn
{
	@Override
	public void run( final String arg )
	{
		final XMLParseResult result = new LoadParseQueryXML().queryXML( true );
		
		if ( result == null )
			return;
		
		final ArrayList< Channel > channels = result.getData().getSequenceDescription().getAllChannels( );
		
		for ( final Angle a : result.getData().getSequenceDescription().getAllAngles() )
			System.out.println( a.getName() );
		
		final GenericDialog gd = new GenericDialog( "Detect Interest Points" );
		
		gd.addMessage("Channels: " + channels.size() );
		
		gd.showDialog();
		
		//gd.addChoice( "Select_type_of_registration", beadRegistration, beadRegistration[ defaultBeadRegistration ] );		
		//gd.addChoice( "Select_type_of_detection", beadDetectionType, beadDetectionType[ defaultBeadDetectionType ] );
	}
	
	public static void main( final String[] args )
	{
		new Detect_Interest_Points().run( null );
	}
}
