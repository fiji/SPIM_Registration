package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.Image_Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.explorer.registration.RegistrationExplorer;

public class FusionPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;

	public FusionPopup()
	{
		super( "Image Fusion/Deconvolution ..." );

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

			if ( new Image_Fusion().fuse( (SpimData2)panel.getSpimData(), panel.selectedRowsViewId() ) )
			{
				// update main table and registration panel if available
				for ( final SelectedViewDescriptionListener l : panel.getListeners() )
					if ( RegistrationExplorer.class.isInstance( l ) )
						( (RegistrationExplorer< ?, ? >)l ).panel().getTableModel().fireTableDataChanged();

				panel.getTableModel().fireTableDataChanged();
			}
		}
	}
}
