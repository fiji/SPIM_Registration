package spim.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;

import mpicbg.spim.data.legacy.LegacyImgLoaderWrapper;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class SlideBook6ImgLoader extends LegacyImgLoaderWrapper< UnsignedShortType, LegacySlideBook6ImgLoader >
{

	public SlideBook6ImgLoader(
			final File sldFile,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription )
	{
		super( new LegacySlideBook6ImgLoader( sldFile, imgFactory, sequenceDescription ) );
	}

	public File getFile() { return legacyImgLoader.getFile(); }
	public ImgFactory< ? extends NativeType< ? > > getImgFactory() { return legacyImgLoader.getImgFactory(); }

	@Override
	public String toString()
	{
		return legacyImgLoader.toString();
	}
}
