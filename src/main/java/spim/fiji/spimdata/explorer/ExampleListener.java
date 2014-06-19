package spim.fiji.spimdata.explorer;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;

public class ExampleListener implements SelectedViewDescriptionListener
{
	final ViewSetupExplorer explorer;
	
	public ExampleListener( final ViewSetupExplorer explorer ) { this.explorer = explorer; }
	
	@Override
	public void seletedViewDescription( final BasicViewDescription<? extends BasicViewSetup> viewDescription )
	{
		System.out.println( "Selected  viewid = " + viewDescription.getViewSetupId() );
		
		if ( viewDescription.getViewSetupId() == 7 )
		{
			explorer.removeListener( this );
			
			if ( explorer.getListeners().size() == 0 ) // just me?
				explorer.quit();
		}
	}
	
	public static void main( String[] args )
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "ViewSetup Explorer", "", false, false, false, false ) )
			return;

		final ViewSetupExplorer explorer = new ViewSetupExplorer( result.getData(), result.getXMLFileName() );
		
		explorer.addListener( new ExampleListener( explorer ) );
		explorer.addListener( new ExampleListener( explorer ) );
	}
}
