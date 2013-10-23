package fiji.plugin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.util.Util;

import fiji.plugin.LoadParseQueryXML.XMLParseResult;
import fiji.spimdata.SpimDataBeads;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class Detect_Interest_Points implements PlugIn
{
	@Override
	public void run( final String arg )
	{
		final XMLParseResult result = new LoadParseQueryXML().queryXML( true );
		
		if ( result == null )
			return;
		
		final int[] channels = getChannels( result.getData() );
		
		System.out.println( Util.printCoordinates( getAngles( result.getData() ) ) );
		
		final GenericDialog gd = new GenericDialog( "Detect Interest Points" );
		
		gd.addMessage("Channels: " + channels.length );
		
		gd.showDialog();
		
		//gd.addChoice( "Select_type_of_registration", beadRegistration, beadRegistration[ defaultBeadRegistration ] );		
		//gd.addChoice( "Select_type_of_detection", beadDetectionType, beadDetectionType[ defaultBeadDetectionType ] );
	}
	
	
	public int[] getChannels( final SpimDataBeads spimData )
	{
		final List< ViewSetup > viewSetups = spimData.getSequenceDescription().getViewSetups();
		final HashSet< Integer > set = new HashSet< Integer >();
		
		for ( final ViewSetup v : viewSetups )
			set.add( v.getChannel().getId() );
		
		final int[] channels = new int[ set.size() ];

		int i = 0;
		for ( final int c : set )
			channels[ i++ ] = c;

		Arrays.sort( channels );
		
		return channels;
	}

	public int[] getAngles( final SpimDataBeads spimData )
	{
		final List< ViewSetup > viewSetups = spimData.getSequenceDescription().getViewSetups();
		final HashSet< Integer > set = new HashSet< Integer >();
		
		for ( final ViewSetup v : viewSetups )
			set.add( v.getAngle().getId() );
		
		final int[] angles = new int[ set.size() ];

		int i = 0;
		for ( final int c : set )
			angles[ i++ ] = c;

		Arrays.sort( angles );
		
		return angles;
	}

	public int[] getNumIlluminationDirections( final SpimDataBeads spimData )
	{
		final List< ViewSetup > viewSetups = spimData.getSequenceDescription().getViewSetups();
		final HashSet< Integer > set = new HashSet< Integer >();
		
		for ( final ViewSetup v : viewSetups )
			set.add( v.getIllumination().getId() );
		
		final int[] illuminations = new int[ set.size() ];

		int i = 0;
		for ( final int c : set )
			illuminations[ i++ ] = c;

		Arrays.sort( illuminations );
		
		return illuminations;
	}
	
	public static void main( final String[] args )
	{
		new Detect_Interest_Points().run( null );
	}
}
