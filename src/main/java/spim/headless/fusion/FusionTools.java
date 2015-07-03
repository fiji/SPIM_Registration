package spim.headless.fusion;

import spim.fiji.spimdata.boundingbox.BoundingBox;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import bdv.img.hdf5.Hdf5ImageLoader;

public class FusionTools
{
	public static boolean matches( final Interval interval, final BoundingBox bb, final int downsampling )
	{
		for ( int d = 0; d < interval.numDimensions(); ++d )
			if ( interval.dimension( d ) != bb.getDimensions( downsampling )[ d ] )
				return false;

		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static < T extends RealType< T > > RandomAccessibleInterval< T > getImage(
			final T type,
			ImgLoader< ? > imgLoader,
			final ViewId view,
			final boolean normalize )
	{
		if ( imgLoader instanceof Hdf5ImageLoader )
			imgLoader = ( ( Hdf5ImageLoader ) imgLoader ).getMonolithicImageLoader();

		if ( (RealType)type instanceof FloatType )
			return (RandomAccessibleInterval)imgLoader.getFloatImage( view, normalize );
		else if ( (RealType)type instanceof UnsignedShortType )
			return (RandomAccessibleInterval)imgLoader.getImage( view );
		else
			return null;
	}

}
