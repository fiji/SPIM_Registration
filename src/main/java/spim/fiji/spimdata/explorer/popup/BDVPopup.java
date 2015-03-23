package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.apply.BigDataViewerTransformationWindow;
import spim.fiji.spimdata.SpimDataWrapper;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import bdv.BigDataViewer;

public class BDVPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;
	BigDataViewer bdv = null;

	public BDVPopup()
	{
		super( "Display in BigDataViewer (on/off)" );

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
					if ( bdv == null )
					{
						if ( AbstractImgLoader.class.isInstance( panel.getSpimData().getSequenceDescription().getImgLoader() ) )
						{
							if ( JOptionPane.showConfirmDialog( null,
									"Opening <SpimData> dataset that is not suited for interactive browsing.\n" +
									"Consider resaving as HDF5 for better performance.\n" +
									"Proceed anyways?",
									"Warning",
									JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
								return;
						}

						// TODO: Remove the wrapper
						try
						{
							bdv = new BigDataViewer( new SpimDataWrapper( panel.getSpimData() ), panel.xml(), null );
						}
						catch (SpimDataException e)
						{
							IOFunctions.println( "Could not run BigDataViewer: " + e );
							e.printStackTrace();
							bdv = null;
						}
					}
					else
					{
						BigDataViewerTransformationWindow.disposeViewerWindow( bdv );
						bdv = null;
					}
				}
			}).start();
		}
	}
}
