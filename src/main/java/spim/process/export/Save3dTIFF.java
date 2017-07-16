package spim.process.export;

import java.io.File;
import java.util.Date;

import fiji.util.gui.GenericDialogPlus;
import ij.ImagePlus;
import ij.io.FileSaver;
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
import spim.fiji.plugin.resave.PluginHelper;
import spim.fiji.plugin.resave.Resave_TIFF;
import spim.process.fusion.FusionTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class Save3dTIFF implements ImgExport
{
	public static boolean defaultUseXMLPath = true;
	public static String defaultPath = null;
	
	String path;
	boolean compress;

	public Save3dTIFF( final String path ) { this( path, false ); }
	public Save3dTIFF( final String path, final boolean compress )
	{ 
		this.path = path;
		this.compress = compress;
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
		return exportImage( img, bb, 1.0, title, fusionGroup, Double.NaN, Double.NaN );
	}

	@SuppressWarnings("unchecked")
	public <T extends RealType<T> & NativeType<T>> boolean exportImage(
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
			minmax = new double[]{ min, max };

		ImagePlus imp = null;

		// TODO: DO NOT DUPLICATE!!!!!!!!
		if ( img instanceof ImagePlusImg )
			try { imp = ((ImagePlusImg<T, ?>)img).getImagePlus(); } catch (ImgLibException e) {}

		if ( imp == null )
			imp = ImageJFunctions.wrap( img, title ).duplicate();

		imp.setTitle( title );

		if ( bb != null )
		{
			imp.getCalibration().xOrigin = -(bb.min( 0 ) / downsampling);
			imp.getCalibration().yOrigin = -(bb.min( 1 ) / downsampling);
			imp.getCalibration().zOrigin = -(bb.min( 2 ) / downsampling);
			imp.getCalibration().pixelWidth = imp.getCalibration().pixelHeight = imp.getCalibration().pixelDepth = downsampling;
		}
		
		imp.setDimensions( 1, (int)img.dimension( 2 ), 1 );
		
		imp.setDisplayRange( minmax[ 0 ], minmax[ 1 ] );
		
		imp.updateAndDraw();

		final String fileName;
		
		if ( !title.endsWith( ".tif" ) )
			fileName = new File( path, title + ".tif" ).getAbsolutePath();
		else
			fileName = new File( path, title ).getAbsolutePath();
		
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
	public boolean queryParameters( final FusionGUI fusion )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Save fused images as 3D TIFF" );

		if ( defaultPath == null || defaultPath.length() == 0 )
		{
			defaultPath = fusion.getSpimData().getBasePath().getAbsolutePath();
			
			if ( defaultPath.endsWith( "/." ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 1 );
			
			if ( defaultPath.endsWith( "/./" ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 2 );
		}

		PluginHelper.addSaveAsDirectoryField( gd, "Output_file_directory", defaultPath, 80 );
		gd.addCheckbox( "Lossless compression of TIFF files (ZIP)", Resave_TIFF.defaultCompress );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		this.path = defaultPath = gd.getNextString().trim();
		this.compress = Resave_TIFF.defaultCompress = gd.getNextBoolean();

		return true;
	}

	@Override
	public ImgExport newInstance() { return new Save3dTIFF( path ); }

	@Override
	public String getDescription() { return "Save as (compressed) TIFF stacks"; }

	@Override
	public boolean finish()
	{
		// nothing to do
		return false;
	}
}
