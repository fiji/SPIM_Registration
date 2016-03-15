package spim.fiji.spimdata.explorer.popup;

import javax.swing.JComponent;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public interface ViewExplorerSetable
{
	public JComponent setViewExplorer( final ViewSetupExplorerPanel< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel );
}
// AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >
