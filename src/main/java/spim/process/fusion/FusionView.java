package spim.process.fusion;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.spimdata.SpimData2;

public class FusionView
{
	final SpimData2 spimData;
	final ViewDescription<TimePoint, ViewSetup> viewDescription;
	final ViewRegistration registration;
	
	public FusionView( final SpimData2 spimData, final ViewDescription<TimePoint, ViewSetup> viewDescription )
	{
		this.spimData = spimData;
		this.viewDescription = viewDescription;
		this.registration = spimData.getViewRegistrations().getViewRegistration( viewDescription );
	}
	
	public ViewRegistration getRegistration() { return registration; }
	public ViewDescription<TimePoint, ViewSetup> getViewDescription() { return viewDescription; }
	
	public RandomAccessibleInterval< FloatType > getImg()
	{
		return spimData.getSequenceDescription().getImgLoader().getImage( viewDescription, false );
	}
}
