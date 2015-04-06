package spim.fiji.spimdata.explorer.popup;

import javax.swing.JSeparator;

import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class Separator extends JSeparator implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	@Override
	public JSeparator setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		return this;
	}
}
