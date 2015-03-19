package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class DisplayViewPopup extends JMenu implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;

	public DisplayViewPopup()
	{
		super( "Display View(s)" );

		final JMenuItem as32bit = new JMenuItem( "As 32 Bit ... " );
		final JMenuItem as16bit = new JMenuItem( "As 16 Bit ... " );

		as16bit.addActionListener( new DisplayViewActionListener( true ) );
		as32bit.addActionListener( new DisplayViewActionListener( false ) );

		this.add( as16bit );
		this.add( as32bit );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel<?, ?> panel )
	{
		this.panel = panel;
		return this;
	}

	public class DisplayViewActionListener implements ActionListener
	{
		final boolean as16bit;

		public DisplayViewActionListener( final boolean as16bit )
		{
			this.as16bit = as16bit;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			// TODO Auto-generated method stub
			System.out.println( "open 16bit: " + as16bit );

			for ( final BasicViewDescription< ? > vd : panel.selectedRows() )
				System.out.println( vd.getTimePointId() + " " + vd.getViewSetupId() );
		}
	}
}
