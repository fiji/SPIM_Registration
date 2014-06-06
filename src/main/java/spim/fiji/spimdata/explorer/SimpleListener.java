package spim.fiji.spimdata.explorer;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;

public class SimpleListener implements SelectedViewDescriptionListener
{
	@Override
	public void seletedViewDescription( final BasicViewDescription<? extends BasicViewSetup> viewDescription )
	{
		System.out.println( "Selected  viewid = " + viewDescription.getViewSetupId() );
	}
}
