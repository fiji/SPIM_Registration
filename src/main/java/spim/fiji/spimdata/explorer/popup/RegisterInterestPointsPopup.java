package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.Interest_Point_Registration;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class RegisterInterestPointsPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;

	public RegisterInterestPointsPopup()
	{
		super( "Register using Interest Points ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( new Interest_Point_Registration().register( (SpimData2)panel.getSpimData(), panel.selectedRowsViewId() ) )
					{
						panel.updateContent(); // update interestpoint and registration panel if available
						ViewSetupExplorerPanel.bdvPopup().updateBDV();
					}
				}
			} ).start();
		}
	}
}
