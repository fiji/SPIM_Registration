package spim.fiji.spimdata.explorer;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.UIManager;

import mpicbg.spim.data.SpimData;

public class ViewSetupExplorer
{
	final JFrame frame;
	final ViewSetupExplorerPanel panel;
	
	public ViewSetupExplorer( final SpimData data )
	{
		try
		{
			UIManager.setLookAndFeel( UIManager.getCrossPlatformLookAndFeelClassName() );
		}
		catch ( Exception e )
		{
			System.out.println( "Could not set look-and-feel" );
		}

		frame = new JFrame( "ViewSetup Explorer" );
		
		frame.addWindowListener( new WindowAdapter()
		{
			public void windowClosing( final WindowEvent evt ) { System.out.println( "Window closing." ); }
		});
		
		panel = new ViewSetupExplorerPanel( data );

		frame.getContentPane().add( panel );
		frame.pack();
		frame.setVisible( true );
	}
	
	public void quit()
	{
		panel.getListeners().clear();
		
		frame.setVisible( false );
		frame.dispose();
	}
	
	public SpimData getSpimData() { return panel.getSpimData(); }
	public ViewSetupExplorerPanel getPanel() { return panel; }
	public JFrame getFrame() { return frame; }
	public void addListener( final SelectedViewDescriptionListener listener ) { panel.addListener( listener ); }
	public boolean removeListener( final SelectedViewDescriptionListener listener ) { return panel.removeListener( listener ); }
	public ArrayList< SelectedViewDescriptionListener > getListeners() { return panel.getListeners(); }

}
