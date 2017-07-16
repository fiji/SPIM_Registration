package spim.process.export;

import ij.ImagePlus;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.plugin.fusion.FusionGUI;
import spim.process.fusion.FusionTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class DisplayImage implements ImgExport
{
	final boolean virtualDisplay;

	public DisplayImage() { this( false ); }
	public DisplayImage( final boolean virtualDisplay ) { this.virtualDisplay = virtualDisplay; }

	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img )
	{
		exportImage( img, null, 1.0, "Image", null );
	}

	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img, final String title )
	{
		exportImage( img, null, 1.0, title, null );
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval< T > img,
			final Interval bb,
			final double downsampling,
			final String title,
			final Group< ? extends ViewId > fusionGroup )
	{
		return exportImage( img, bb, downsampling, title, fusionGroup, Double.NaN, Double.NaN );
	}

	public < T extends RealType< T > & NativeType< T > > boolean exportImage(
			final RandomAccessibleInterval<T> img,
			final Interval bb,
			final double downsampling,
			final String title,
			final Group< ? extends ViewId > fusionGroup,
			final double min,
			final double max )
	{
		// do nothing in case the image is null
		if ( img == null )
			return false;

		// determine min and max
		final double[] minmax;

		if ( Double.isNaN( min ) || Double.isNaN( max ) )
			minmax = FusionTools.minMaxApprox( img );
		else
			minmax = new double[]{ (float)min, (float)max };

		IOFunctions.println( "Approximate min=" + minmax[ 0 ] + ", max=" + minmax[ 1 ] );

		ImagePlus imp = getImagePlusInstance( img, virtualDisplay, title, minmax[ 0 ], minmax[ 1 ] );

		if ( bb != null )
		{
			imp.getCalibration().xOrigin = -(bb.min( 0 ) / downsampling);
			imp.getCalibration().yOrigin = -(bb.min( 1 ) / downsampling);
			imp.getCalibration().zOrigin = -(bb.min( 2 ) / downsampling);
			imp.getCalibration().pixelWidth = imp.getCalibration().pixelHeight = imp.getCalibration().pixelDepth = downsampling;
		}

		imp.updateAndDraw();
		imp.show();

		return true;
	}

	@SuppressWarnings("unchecked")
	public static < T extends RealType< T > & NativeType< T > > ImagePlus getImagePlusInstance(
			final RandomAccessibleInterval< T > img,
			final boolean virtualDisplay,
			final String title,
			final double min,
			final double max )
	{
		ImagePlus imp = null;

		if ( img instanceof ImagePlusImg )
			try { imp = ((ImagePlusImg<T, ?>)img).getImagePlus(); } catch (ImgLibException e) {}

		if ( imp == null )
		{
			if ( virtualDisplay )
				imp = ImageJFunctions.wrap( img, title );
			else
				imp = ImageJFunctions.wrap( img, title ).duplicate();
		}

		imp.setTitle( title );
		imp.setDimensions( 1, (int)img.dimension( 2 ), 1 );
		imp.setDisplayRange( min, max );

		return imp;
	}

	@Override
	public boolean queryParameters( final FusionGUI fusion ) { return true; }

	@Override
	public ImgExport newInstance() { return new DisplayImage(); }

	@Override
	public String getDescription() { return "Display using ImageJ"; }

	@Override
	public boolean finish()
	{
		// this spimdata object was not modified
		return false;
	}
}
