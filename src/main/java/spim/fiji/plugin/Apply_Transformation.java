package spim.fiji.plugin;

import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import ij.plugin.PlugIn;

public class Apply_Transformation implements PlugIn
{
	@Override
	public void run( final String arg0 )
	{
		// ask for everything
		final XMLParseResult result = new LoadParseQueryXML().queryXML( "applying a transformation", "Apply to", true, true, true, true );
		
		if ( result == null )
			return;
		
		
		
	}

	/**
	 * @param args
	 */
	public static void main( final String[] args )
	{
		new Apply_Transformation().run( null );
	}
}
