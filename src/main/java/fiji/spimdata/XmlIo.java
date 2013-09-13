package fiji.spimdata;

import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.XmlIoImgLoader;
import mpicbg.spim.data.sequence.XmlIoMissingViews;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;
import mpicbg.spim.data.sequence.XmlIoTimePoints;
import fiji.spimdata.sequence.ViewSetupBeads;
import fiji.spimdata.sequence.XmlIoViewSetupsBeads;

public class XmlIo
{
	public static XmlIoSpimData< TimePoint, ViewSetupBeads > createDefaultIo()
	{
		final XmlIoSequenceDescription< TimePoint, ViewSetupBeads > seqDesc = 
				new XmlIoSequenceDescription< TimePoint, ViewSetupBeads >( new XmlIoTimePoints(), new XmlIoViewSetupsBeads(), new XmlIoMissingViews(), new XmlIoImgLoader() );
		final XmlIoSpimData< TimePoint, ViewSetupBeads > io = 
				new XmlIoSpimData< TimePoint, ViewSetupBeads >( seqDesc, new XmlIoViewRegistrations() );
			
		return io;
	}

}
