package spim.fiji.plugin;

import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import ij.plugin.PlugIn;

/**
 *
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Interest_Point_Registration implements PlugIn
{

	@Override
	public void run( final String arg )
	{
		final XMLParseResult result = new LoadParseQueryXML().queryXML( false );

		if ( result == null )
			return;

	}

	public static void main( String[] args )
	{
		new Interest_Point_Registration().run( null );
	}
}