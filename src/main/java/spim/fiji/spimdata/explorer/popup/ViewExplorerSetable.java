package spim.fiji.spimdata.explorer.popup;

import javax.swing.JMenuItem;

import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public interface ViewExplorerSetable
{
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel );
}
