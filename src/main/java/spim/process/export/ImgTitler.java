package spim.process.export;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;

public interface ImgTitler
{
	public String getImageTitle( final TimePoint tp, final ViewSetup vs );
}
