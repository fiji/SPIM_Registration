package spim.fiji.spimdata.explorer;

import java.util.List;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;

public interface ExplorerWindow< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > >
{
	public List< BasicViewDescription< ? extends BasicViewSetup > > selectedRows();
	public List< ViewId > selectedRowsViewId();
	public AS getSpimData();
	public void updateContent();
	public String xml();
	public void saveXML();

	// BDV-specific
	public boolean colorMode();
	public BasicViewDescription< ? extends BasicViewSetup > firstSelectedVD();
}
