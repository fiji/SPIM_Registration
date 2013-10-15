package fiji.spimdata;

import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import mpicbg.spim.data.sequence.ImgLoader;

public abstract class AbstractImgLoader implements ImgLoader
{
	public static ImgFactory< FloatType > preferredImgFactory = new ArrayImgFactory< FloatType >();
	
	protected ImgFactory< FloatType > imgFactory;
	
	public AbstractImgLoader(){ this( preferredImgFactory ); }
	public AbstractImgLoader( final ImgFactory< FloatType > imgFactory ){ this.imgFactory = imgFactory; }
	
	public ImgFactory< FloatType > getImgFactory() { return imgFactory; }
	public void setImgFactory( final ImgFactory< FloatType > imgFactory ) { this.imgFactory = imgFactory; }
}
