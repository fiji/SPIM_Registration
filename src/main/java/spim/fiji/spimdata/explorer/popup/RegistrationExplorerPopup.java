package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.explorer.registration.RegistrationExplorer;

public class RegistrationExplorerPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;
	RegistrationExplorer< ?, ? > re = null;

	public RegistrationExplorerPopup()
	{
		super( "Registration Explorer (on/off)" );

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

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( re == null || !re.frame().isVisible() )
					{
						re = instanceFor( (ViewSetupExplorerPanel)panel );
					}
					else
					{
						re.quit();
						re = null;
					}
				}
			}).start();
		}
	}

	private static final < AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > > RegistrationExplorer< AS, X > instanceFor( final ViewSetupExplorerPanel< AS, X > panel )
	{
		return new RegistrationExplorer< AS, X >( panel.xml(), panel.io(), panel.explorer() );
	}
}
