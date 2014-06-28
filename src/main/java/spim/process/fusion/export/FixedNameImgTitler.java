package spim.process.fusion.export;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;

public class FixedNameImgTitler implements ImgTitler
{
	String title;

	public FixedNameImgTitler( final String title ) { this.title = title; }

	public void setTitle( final String title ) { this.title = title; }

	@Override
	public String getImageTitle( final TimePoint tp, final ViewSetup vs ) { return title; }
}
