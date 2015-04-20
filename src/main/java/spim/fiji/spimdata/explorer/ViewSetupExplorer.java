package spim.fiji.spimdata.explorer;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.swing.JFrame;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;

public class ViewSetupExplorer< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > >
{
	final JFrame frame;
	ViewSetupExplorerPanel< AS, X > panel;
	
	public ViewSetupExplorer( final AS data, final String xml, final X io )
	{
		frame = new JFrame( "ViewSetup Explorer" );
		panel = new ViewSetupExplorerPanel< AS, X >( this, data, xml, io );

		frame.add( panel, BorderLayout.CENTER );
		frame.setSize( panel.getPreferredSize() );

		frame.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowClosing( WindowEvent evt )
					{
						quit();
					}
				});

		frame.pack();
		frame.setVisible( true );

		// set the initial focus to the table
		panel.table.requestFocus();
	}
	
	public void quit()
	{
		for ( final SelectedViewDescriptionListener< AS > l : panel.getListeners() )
			l.quit();

		panel.getListeners().clear();
		
		frame.setVisible( false );
		frame.dispose();
	}
	
	public AS getSpimData() { return panel.getSpimData(); }
	public ViewSetupExplorerPanel< AS, X > getPanel() { return panel; }
	public JFrame getFrame() { return frame; }
	public void addListener( final SelectedViewDescriptionListener< AS > listener ) { panel.addListener( listener ); }
	public ArrayList< SelectedViewDescriptionListener< AS > > getListeners() { return panel.getListeners(); }
}
