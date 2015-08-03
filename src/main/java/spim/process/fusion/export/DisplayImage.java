package spim.process.fusion.export;

import ij.ImagePlus;
import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.boundingbox.BoundingBoxGUI;

public class DisplayImage implements ImgExportTitle
{
	final boolean virtualDisplay;
	ImgTitler imgTitler = new DefaultImgTitler();

	public DisplayImage() { this( false ); }
	public DisplayImage( final boolean virtualDisplay ) { this.virtualDisplay = virtualDisplay; }

	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img, final String title )
	{
		final ImgTitler current = this.getImgTitler();
		this.setImgTitler( new FixedNameImgTitler( title ) );

		exportImage( img, null, null, null );

		this.setImgTitler( current );
	}

	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img, final BoundingBoxGUI bb, final String title )
	{
		final ImgTitler current = this.getImgTitler();
		this.setImgTitler( new FixedNameImgTitler( title ) );

		exportImage( img, bb, null, null );

		this.setImgTitler( current );
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval< T > img, final BoundingBoxGUI bb, final TimePoint tp, final ViewSetup vs )
	{
		return exportImage( img, bb, tp, vs, Double.NaN, Double.NaN );
	}

	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval<T> img, final BoundingBoxGUI bb, final TimePoint tp, final ViewSetup vs, final double min, final double max )
	{
		// do nothing in case the image is null
		if ( img == null )
			return false;
		
		// determine min and max
		final float[] minmax;
		
		if ( Double.isNaN( min ) || Double.isNaN( max ) )
			minmax = FusionHelper.minMax( img );
		else
			minmax = new float[]{ (float)min, (float)max };

		ImagePlus imp = getImagePlusInstance( img, virtualDisplay, getImgTitler().getImageTitle( tp, vs ), minmax[ 0 ], minmax[ 1 ] );

		if ( bb != null )
		{
			imp.getCalibration().xOrigin = -(bb.min( 0 ) / bb.getDownSampling());
			imp.getCalibration().yOrigin = -(bb.min( 1 ) / bb.getDownSampling());
			imp.getCalibration().zOrigin = -(bb.min( 2 ) / bb.getDownSampling());
			imp.getCalibration().pixelWidth = imp.getCalibration().pixelHeight = imp.getCalibration().pixelDepth = bb.getDownSampling();
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
	public boolean queryParameters( final SpimData2 spimData, final boolean is16bit ) { return true; }

	@Override
	public void queryAdditionalParameters( final GenericDialog gd, final SpimData2 spimData ) {}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd, final SpimData2 spimData ) { return true; }

	@Override
	public ImgExport newInstance() { return new DisplayImage(); }

	@Override
	public String getDescription() { return "Display using ImageJ"; }

	@Override
	public void setImgTitler( final ImgTitler imgTitler ) { this.imgTitler = imgTitler; }

	@Override
	public ImgTitler getImgTitler() { return imgTitler; }

	@Override
	public void setXMLData( final List< TimePoint > timepointsToProcess, final List< ViewSetup > newViewSetups ) {}

	@Override
	public boolean finish()
	{
		// this spimdata object was not modified
		return false;
	}
}
