package spim.fiji.spimdata.imgloaders.flatfield;

import java.io.File;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;

public interface FlatfieldCorrectionWrappedImgLoader<IL extends ImgLoader>
{
	public IL getWrappedImgLoder();
	public void setActive(boolean active);
	public boolean isActive();
	public void setBrightImage(ViewId vId, File imgFile);
	public void setDarkImage(ViewId vId, File imgFile);
}
