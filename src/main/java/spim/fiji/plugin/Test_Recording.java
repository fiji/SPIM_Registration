package spim.fiji.plugin;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import ij.plugin.PlugIn;

public class Test_Recording implements PlugIn
{

	@Override
	public void run(String arg0)
	{
		// ask for everything, underscores will be inserted by the LoadParseQuery class
		final XMLParseResult result = new LoadParseQueryXML().queryXML( "testing macro recording", "Display name of", true, true, true, true );
		
		if ( result == null )
			return;
		
		for ( final TimePoint t : result.getTimePointsToProcess() )
			for ( final Channel c : result.getChannelsToProcess() )
				for ( final Illumination i : result.getIlluminationsToProcess() )
					for ( final Angle a : result.getAnglesToProcess() )
					{
						IOFunctions.println( t.getName() + " " + c.getName() + " " + i.getName() + " " + a.getName() );
					}
	}

}
