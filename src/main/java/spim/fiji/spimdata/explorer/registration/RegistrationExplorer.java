package spim.fiji.spimdata.explorer.registration;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.explorer.ViewSetupExplorer;

public class RegistrationExplorer implements SelectedViewDescriptionListener
{
	final SpimData data;
	final String xml;
	final JFrame frame;
	final RegistrationExplorerPanel panel;
	
	public RegistrationExplorer( final SpimData data, final String xml )
	{
		this.data = data;
		this.xml = xml;
		
		final ViewSetupExplorer viewSetupExplorer = new ViewSetupExplorer( data, xml );
		
		frame = new JFrame( "Registration Explorer" );
		panel = new RegistrationExplorerPanel( data.getViewRegistrations() );
		frame.add( panel, BorderLayout.CENTER );

		frame.setSize( panel.getPreferredSize() );
		
		frame.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowClosing( WindowEvent evt )
					{
						viewSetupExplorer.quit();
					}
				});

		frame.pack();
		frame.setVisible( true );
		
		// Get the size of the screen
		final Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

		// Move the window
		frame.setLocation( ( dim.width - frame.getSize().width ) / 2, ( dim.height - frame.getSize().height ) / 2 );

		// this call also triggers the first update of the registration table
		viewSetupExplorer.addListener( this );
	}
		
	@Override
	public void seletedViewDescription( final BasicViewDescription<? extends BasicViewSetup> viewDescription )
	{
		panel.updateViewDescription( viewDescription );
	}
	
	public static void main( String[] args )
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "View Registration Explorer", "", false, false, false, false ) )
			return;
		
		new RegistrationExplorer( result.getData(), result.getXMLFileName() );
	}

}
