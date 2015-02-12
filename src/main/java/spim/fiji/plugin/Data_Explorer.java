package spim.fiji.plugin;

import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.explorer.ViewSetupExplorer;
import spim.fiji.spimdata.explorer.interestpoint.InterestPointExplorer;
import spim.fiji.spimdata.explorer.registration.RegistrationExplorer;
import ij.plugin.PlugIn;

public class Data_Explorer implements PlugIn
{
	@Override
	public void run( String arg )
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Interest Point Explorer", "", false, false, false, false ) )
			return;

		final ViewSetupExplorer< SpimData2, XmlIoSpimData2 > explorer = new ViewSetupExplorer<SpimData2, XmlIoSpimData2 >( result.getData(), result.getXMLFileName(), result.getIO() );

		new InterestPointExplorer< SpimData2, XmlIoSpimData2 >( result.getData(), result.getXMLFileName(), result.getIO(), explorer );
		new RegistrationExplorer< SpimData2, XmlIoSpimData2 >( result.getData(), result.getXMLFileName(), result.getIO(), explorer );
	}

	public static void main( String[] args )
	{
		new Data_Explorer().run( null );
	}
}
