package spim.fiji.spimdata.imgloaders;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;

public abstract class AbstractImgLoader implements ImgLoader
{
	protected ImgFactory< ? extends NativeType< ? > > imgFactory = null;
	
	/**
	 * Updates the ViewSetup if the respective values are still set to -1
	 * 
	 * @param viewDescription
	 * @param dim
	 */
	protected void updateXMLMetaData( final ViewDescription< ?, ? > viewDescription, 
			final int w, final int h, final int d,
			final double calX, final double calY, final double calZ,
			final boolean forceUpdate )
	{
		final ViewSetup v = viewDescription.getViewSetup();
		
		if ( v.getWidth() == - 1 || forceUpdate )
		{
			v.setWidth( w );
			v.setHeight( h );
			v.setDepth( d );
		}
		
		if ( v.getPixelWidth() == -1 || forceUpdate )
		{
			if ( !Double.isNaN( calX ) && !Double.isInfinite( calX ) )
				v.setPixelWidth( calX );

			if ( !Double.isNaN( calY ) && !Double.isInfinite( calY ) )
				v.setPixelHeight( calY );
			
			if ( !Double.isNaN( calZ ) && !Double.isInfinite( calZ ) )
				v.setPixelDepth( calZ );
		}
	}
		
	public ImgFactory< ? extends NativeType< ? > > getImgFactory() { return imgFactory; }
	public void setImgFactory( final ImgFactory< ? extends NativeType< ? > > imgFactory ) { this.imgFactory = imgFactory; }
}
