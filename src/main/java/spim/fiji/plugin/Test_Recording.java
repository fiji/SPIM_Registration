package spim.fiji.plugin;

import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.io.IOFunctions;

public class Test_Recording implements PlugIn
{
	@Override
	public void run(String arg0)
	{
		// ask for everything, underscores will be inserted by the LoadParseQuery class
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "for testing", true, true, true, true ) )
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
