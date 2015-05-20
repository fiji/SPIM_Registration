package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class RemoveTransformationPopup extends JMenu implements ViewExplorerSetable
{
	public static final int askWhenMoreThan = 5;
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;

	protected static String[] types = new String[]{ "Latest/Newest Transformation", "First/Oldest Transformation" };

	public RemoveTransformationPopup()
	{
		super( "Remove Transformation" );

		final JMenuItem lastest = new JMenuItem( types[ 0 ] );
		final JMenuItem oldest = new JMenuItem( types[ 1 ] );

		lastest.addActionListener( new MyActionListener( 0 ) );
		oldest.addActionListener( new MyActionListener( 1 ) );

		this.add( lastest );
		this.add( oldest );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		final int index; // 0 == latest, 1 == first

		public MyActionListener( final int index )
		{
			this.index = index;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			//final AbstractSpimData< ? > data = (AbstractSpimData< ? >)panel.getSpimData();
			final List< ViewId > viewIds = panel.selectedRowsViewId();

			final ViewRegistrations vr = panel.getSpimData().getViewRegistrations();
			for ( final ViewId viewId : viewIds )
			{
				final ViewRegistration v = vr.getViewRegistrations().get( viewId );
				
				if ( index == 0 )
					v.getTransformList().remove( 0 );
				else
					v.getTransformList().remove( v.getTransformList().size() - 1 );

				v.updateModel();
			}

			panel.updateContent();
			ViewSetupExplorerPanel.bdvPopup().updateBDV();
		}
	}
}
