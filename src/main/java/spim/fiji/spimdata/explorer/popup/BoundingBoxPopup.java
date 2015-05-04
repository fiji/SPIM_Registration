package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.Define_Bounding_Box;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class BoundingBoxPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;

	public BoundingBoxPopup()
	{
		super( "Define Bounding Box ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JComponent setViewExplorer( ViewSetupExplorerPanel<? extends AbstractSpimData<? extends AbstractSequenceDescription<?, ?, ?>>, ?> panel )
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
					if ( new Define_Bounding_Box().defineBoundingBox( (SpimData2)panel.getSpimData(), panel.selectedRowsViewId() ) != null )
					{
						panel.updateContent(); // update main table and registration panel if available
						ViewSetupExplorerPanel.bdvPopup().updateBDV();
					}
				}
			} ).start();
		}
	}
}
