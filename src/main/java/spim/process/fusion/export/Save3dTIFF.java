package spim.process.fusion.export;

import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.io.FileSaver;

import java.io.File;
import java.util.Date;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.boundingbox.BoundingBoxGUI;

public class Save3dTIFF implements ImgExportTitle
{
	public static boolean defaultUseXMLPath = true;
	public static String defaultPath = null;
	
	String path;
	boolean compress;
	
	ImgTitler imgTitler = new DefaultImgTitler();
	
	public Save3dTIFF( final String path ) { this( path, false ); }
	public Save3dTIFF( final String path, final boolean compress )
	{ 
		this.path = path;
		this.compress = compress;
	}
	
	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img, final String title )
	{
		final ImgTitler current = this.getImgTitler();
		this.setImgTitler( new FixedNameImgTitler( title ) );
		
		exportImage( img, null, null, null );
		
		this.setImgTitler( current );
	}

	@Override
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval< T > img, final BoundingBoxGUI bb, final TimePoint tp, final ViewSetup vs )
	{
		return exportImage( img, bb, tp, vs, Double.NaN, Double.NaN );
	}

	@SuppressWarnings("unchecked")
	public <T extends RealType<T> & NativeType<T>> boolean exportImage( final RandomAccessibleInterval<T> img, final BoundingBoxGUI bb, final TimePoint tp, final ViewSetup vs, final double min, final double max )
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

		ImagePlus imp = null;
		
		if ( img instanceof ImagePlusImg )
			try { imp = ((ImagePlusImg<T, ?>)img).getImagePlus(); } catch (ImgLibException e) {}

		if ( imp == null )
			imp = ImageJFunctions.wrap( img, getImgTitler().getImageTitle( tp, vs ) ).duplicate();

		imp.setTitle( getImgTitler().getImageTitle( tp, vs ) );

		if ( bb != null )
		{
			imp.getCalibration().xOrigin = -(bb.min( 0 ) / bb.getDownSampling());
			imp.getCalibration().yOrigin = -(bb.min( 1 ) / bb.getDownSampling());
			imp.getCalibration().zOrigin = -(bb.min( 2 ) / bb.getDownSampling());
			imp.getCalibration().pixelWidth = imp.getCalibration().pixelHeight = imp.getCalibration().pixelDepth = bb.getDownSampling();
		}
		
		imp.setDimensions( 1, (int)img.dimension( 2 ), 1 );
		
		imp.setDisplayRange( minmax[ 0 ], minmax[ 1 ] );
		
		imp.updateAndDraw();

		final String fileName;
		
		if ( !getImgTitler().getImageTitle( tp, vs ).endsWith( ".tif" ) )
			fileName = new File( path, getImgTitler().getImageTitle( tp, vs ) + ".tif" ).getAbsolutePath();
		else
			fileName = new File( path, getImgTitler().getImageTitle( tp, vs ) ).getAbsolutePath();
		
		if ( compress )
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving file " + fileName + ".zip" );
			return new FileSaver( imp ).saveAsZip( fileName );
		}
		else
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Saving file " + fileName );
			return new FileSaver( imp ).saveAsTiffStack( fileName );
		}
	}

	@Override
	public boolean queryParameters( final SpimData2 spimData, final boolean is16bit ) { return true; }

	@Override
	public void queryAdditionalParameters( final GenericDialog gd, final SpimData2 spimData )
	{
		if ( defaultPath == null || defaultPath.length() == 0 )
		{
			defaultPath = spimData.getBasePath().getAbsolutePath();
			
			if ( defaultPath.endsWith( "/." ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 1 );
			
			if ( defaultPath.endsWith( "/./" ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 2 );
		}

		gd.addStringField( "Output_file_directory", defaultPath, 50 );
	}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd, final SpimData2 spimData )
	{
		this.path = gd.getNextString().trim();
		
		return true;
	}

	@Override
	public ImgExport newInstance() { return new Save3dTIFF( path ); }

	@Override
	public String getDescription() { return "Save as TIFF stack"; }

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
