package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.Interest_Point_Detection;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.explorer.interestpoint.InterestPointExplorer;

public class DetectInterestPointsPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;

	public DetectInterestPointsPopup()
	{
		super( "Detect Interest Points ..." );

		this.addActionListener( new DetectInterestPointsActionListener() );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class DetectInterestPointsActionListener implements ActionListener
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

			if ( new Interest_Point_Detection().detectInterestPoints( (SpimData2)panel.getSpimData(), panel.selectedRowsViewId() ) )
			{
				// update interestpoint panel if available
				for ( final SelectedViewDescriptionListener l : panel.getListeners() )
					if ( InterestPointExplorer.class.isInstance( l ) )
						( (InterestPointExplorer< ?, ? >)l ).panel().getTableModel().fireTableDataChanged();
			}
		}
	}
}
