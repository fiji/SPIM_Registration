package spim.fiji.spimdata.explorer;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;

public interface SelectedViewDescriptionListener
{
	public void seletedViewDescription( BasicViewDescription< ? extends BasicViewSetup > viewDescription );
	public void save();
	public void quit();
}
