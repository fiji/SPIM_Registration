package spim.fiji.spimdata;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;

public class SpimDataWrapper extends SpimDataMinimal
{
	/**
	 * create copy of a {@link SpimDataMinimal} with replaced {@link BasicImgLoader}
	 */
	public SpimDataWrapper( final AbstractSpimData< ? > other )
	{
		super(
				other.getBasePath(),
				new SequenceDescriptionMinimal(
						other.getSequenceDescription().getTimePoints(),
						other.getSequenceDescription().getViewSetups(),
						other.getSequenceDescription().getImgLoader(),
						other.getSequenceDescription().getMissingViews() ),
				other.getViewRegistrations() );
	}
}
