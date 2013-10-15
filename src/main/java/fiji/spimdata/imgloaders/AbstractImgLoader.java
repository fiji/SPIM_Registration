package fiji.spimdata.imgloaders;

import mpicbg.spim.data.sequence.ImgLoader;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;

public abstract class AbstractImgLoader implements ImgLoader
{
	protected ImgFactory< ? extends NativeType< ? > > imgFactory = null;
		
	public ImgFactory< ? extends NativeType< ? > > getImgFactory() { return imgFactory; }
	public void setImgFactory( final ImgFactory< ? extends NativeType< ? > > imgFactory ) { this.imgFactory = imgFactory; }
}
