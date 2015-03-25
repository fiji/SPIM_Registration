package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.Display_View;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class DisplayViewPopup extends JMenu implements ViewExplorerSetable
{
	public static final int askWhenMoreThan = 5;
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;

	public DisplayViewPopup()
	{
		super( "Display View(s)" );

		final JMenuItem as32bit = new JMenuItem( "As 32-Bit (ImageJ Stack)" );
		final JMenuItem as16bit = new JMenuItem( "As 16-Bit (ImageJ Stack)" );

		as16bit.addActionListener( new MyActionListener( true ) );
		as32bit.addActionListener( new MyActionListener( false ) );

		this.add( as16bit );
		this.add( as32bit );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		final boolean as16bit;

		public MyActionListener( final boolean as16bit )
		{
			this.as16bit = as16bit;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final List< BasicViewDescription< ? extends BasicViewSetup > > vds = panel.selectedRows();

					if (
						vds.size() > askWhenMoreThan &&
						JOptionPane.showConfirmDialog(
							null,
							"Are you sure to display " + vds.size() + " views?",
							"Warning",
							JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
						return;

					IOFunctions.println(
							"Opening as" + ( as16bit ? " 16 bit" : "32 bit" ) + " using " +
							panel.getSpimData().getSequenceDescription().getImgLoader().getClass().getSimpleName() );

					for ( final BasicViewDescription< ? > vd : panel.selectedRows() )
					{
						IOFunctions.println( "Loading timepoint: " + vd.getTimePointId() + " ViewSetup: " + vd.getViewSetupId() );
		
						final String name;
		
						if ( SpimData2.class.isInstance( panel.getSpimData() ) )
							name = Display_View.name( (ViewDescription)vd );
						else
							name = "Timepoint: " + vd.getTimePointId() + " ViewSetup: " + vd.getViewSetupId();
			
						if ( as16bit )
							Display_View.display( panel.getSpimData(), vd, 1, name );
						else
							Display_View.display( panel.getSpimData(), vd, 0, name );
					}
				}
			} ).start();
		}
	}
}
