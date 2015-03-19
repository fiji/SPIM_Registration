package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class DetectInterestPointsPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel<?, ?> panel;

	public DetectInterestPointsPopup()
	{
		super( "Detect Interest Points" );

		this.addActionListener( new DetectInterestPointsActionListener() );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel<?, ?> panel )
	{
		this.panel = panel;
		return this;
	}

	public class DetectInterestPointsActionListener implements ActionListener
	{
		
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			// TODO Auto-generated method stub
			System.out.println( "pressed ip detection" );
		}
	}

}
