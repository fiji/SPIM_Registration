package spim.fiji.spimdata.pointspreadfunctions;

import java.io.File;

import ij.ImagePlus;
import ij.io.FileSaver;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.spimdata.SpimData2;
import spim.process.export.DisplayImage;

public class PointSpreadFunction
{
	private final static String subDir = "psf";

	private final File xmlBasePath;
	private final String file;
	private Img< FloatType > img;
	private boolean modified;

	public PointSpreadFunction( final File xmlBasePath, final String file, final Img< FloatType > img )
	{
		this.xmlBasePath = xmlBasePath;
		this.file = file;
		this.img = img.copy(); // avoid changes to the PSF
		this.modified = true; // not initialized from disc, needs to be saved
	}

	public PointSpreadFunction( final SpimData2 spimData, final ViewId viewId, final Img< FloatType > img  )
	{
		this( spimData.getBasePath(), PointSpreadFunction.createPSFFileName( viewId ), img );
	}

	public PointSpreadFunction( final File xmlBasePath, final String file )
	{
		this( xmlBasePath, file, null );
		this.modified = false;
	}

	public void setPSF( final Img< FloatType > img )
	{
		this.modified = true;
		this.img = img;
	}

	public String getFile() { return file; }
	public boolean isModified() { return modified; }
	public boolean isLoaded() { return img != null; }

	public Img< FloatType > getPSFCopy()
	{
		if ( img == null )
			img = IOFunctions.openAs32Bit( new File( new File( xmlBasePath, subDir ), file ), new ArrayImgFactory<>() );

		return img.copy();
	}

	public boolean save()
	{
		if ( img == null )
			return false;

		final File dir = new File( xmlBasePath, subDir );

		if ( !dir.exists() )
			if ( !dir.mkdir() )
				return false;

		final ImagePlus imp = DisplayImage.getImagePlusInstance( img, false, file, 0, 1 );
		return new FileSaver( imp ).saveAsTiffStack( new File( dir, file ).toString() );
	}

	public static String createPSFFileName( final ViewId viewId )
	{
		return "psf_t" + viewId.getTimePointId() + "_v" + viewId.getViewSetupId() + ".tif";
	}
}
