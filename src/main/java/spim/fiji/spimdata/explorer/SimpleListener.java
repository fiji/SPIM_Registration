package spim.fiji.spimdata.explorer;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import spim.fiji.plugin.LoadParseQueryXML;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;

public class SimpleListener implements SelectedViewDescriptionListener
{
	final ViewSetupExplorer explorer;
	
	public SimpleListener( final ViewSetupExplorer explorer ) { this.explorer = explorer; }
	
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
		final XMLParseResult result = new LoadParseQueryXML().queryXML( "ViewSetup Explorer", "", false, false, false, false );
		
		if ( result == null )
			return;

		final ViewSetupExplorer explorer = new ViewSetupExplorer( result.getData() );
		
		explorer.addListener( new SimpleListener( explorer ) );
		explorer.addListener( new SimpleListener( explorer ) );
	}
}
