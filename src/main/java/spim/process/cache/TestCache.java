package spim.process.cache;

import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import spim.process.fusion.weights.Blending;
import spim.process.fusion.weights.TransformedRealRandomAccessibleInterval;

public class TestCache
{

	public static void main( String[] args )
	{
		final FinalInterval interval = new FinalInterval( new long[]{ 0, 0, 0 }, new long[]{ 500, 400, 300 } );
		final Blending blend = new Blending(
				interval,
				new float[]{ 100, 0, 20 },
				new float[]{ 12, 150, 30 } );

		final TransformedRealRandomAccessibleInterval< FloatType> img = new TransformedRealRandomAccessibleInterval< FloatType >(
				blend,
				new FloatType( -1 ),
				interval,
				new AffineTransform3D(),
				new long[]{ 0, 0, 0 } );

		ImagePlus imp = ImageJFunctions.show( img );
		imp.setDisplayRange( 0, 1 );
		imp.updateAndDraw();
	}
}
