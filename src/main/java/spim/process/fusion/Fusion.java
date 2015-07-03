package spim.process.fusion;

import java.util.List;
import java.util.Map;

import spim.fiji.spimdata.boundingbox.BoundingBox;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public interface Fusion
{
	
	public < T extends RealType< T > & NativeType< T > > Img< T > fuse(
			final T type,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final Map< ViewId, AffineTransform3D > registrationMap,
			final Map< ViewId, RandomAccessibleInterval< T > > imgMap,
			final BoundingBox bb
			);

}
