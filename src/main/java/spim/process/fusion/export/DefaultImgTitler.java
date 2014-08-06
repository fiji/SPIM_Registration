package spim.process.fusion.export;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;

public class DefaultImgTitler implements ImgTitler
{
	@Override
	public String getImageTitle( final TimePoint tp, final ViewSetup vs )
	{
		return "Timepoint" + tp.getId() + "_Channel" + vs.getChannel().getName() + "_Illum" + vs.getIllumination().getName() + "_Angle" + vs.getAngle().getName();
	}

}
