package spim.fiji.spimdata.explorer.popup;

import javax.swing.JComponent;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import spim.fiji.spimdata.explorer.ExplorerWindow;

public interface ExplorerWindowSetable
{
	public JComponent setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel );
}
// AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >
