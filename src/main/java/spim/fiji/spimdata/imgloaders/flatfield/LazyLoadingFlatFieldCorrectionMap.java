package spim.fiji.spimdata.imgloaders.flatfield;

import java.io.File;
import java.util.Map;

import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public abstract class LazyLoadingFlatFieldCorrectionMap<IL extends ImgLoader> implements FlatfieldCorrectionWrappedImgLoader< IL >
{
	
	private Map< File, RandomAccessibleInterval< ? extends RealType<?> > > raiMap;
	private Map<ViewId, Pair<File, File>> fileMap;
	
	@Override
	public void setBrightImage(ViewId vId, File imgFile)
	{
		if (!fileMap.containsKey( vId ))
			fileMap.put( vId, new ValuePair< File, File >( null, null ) );

		final Pair< File, File > oldPair = fileMap.get( vId );
		fileMap.put( vId, new ValuePair< File, File >( imgFile, oldPair.getB() ) );
	}

	@Override
	public void setDarkImage(ViewId vId, File imgFile)
	{
		if (!fileMap.containsKey( vId ))
			fileMap.put( vId, new ValuePair< File, File >( null, null ) );

		final Pair< File, File > oldPair = fileMap.get( vId );
		fileMap.put( vId, new ValuePair< File, File >( oldPair.getA(), imgFile ) );
	}
	
	protected RandomAccessibleInterval< ? extends RealType<?> > getBrightImg(ViewId vId)
	{
		if (!fileMap.containsKey( vId ))
			return null;

		final File fileToLoad = fileMap.get( vId ).getA();

		if (fileToLoad == null)
			return null;

		loadFileIfNecessary( fileToLoad );
		return raiMap.get( fileToLoad );
	}

	protected RandomAccessibleInterval< ? extends RealType<?> > getDarkImg(ViewId vId)
	{
		if (!fileMap.containsKey( vId ))
			return null;

		final File fileToLoad = fileMap.get( vId ).getB();

		if (fileToLoad == null)
			return null;

		loadFileIfNecessary( fileToLoad );
		return raiMap.get( fileToLoad );
	}
	
	protected void loadFileIfNecessary(File file)
	{
		if (raiMap.containsKey( file ))
			return;
		
		final ImagePlus imp = IJ.openImage( file.getAbsolutePath() );
		final RandomAccessibleInterval< ? extends RealType<?> > img = ImageJFunctions.wrapReal( imp ).copy();
		
		raiMap.put( file, img );
	}
	

}
