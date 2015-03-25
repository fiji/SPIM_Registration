package spim.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

public class MicroManagerImgLoader extends AbstractImgLoader
{
	final File mmFile;
	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;

	public MicroManagerImgLoader(
			final File mmFile,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription<?, ?, ?> sequenceDescription )
	{
		super();
		this.mmFile = mmFile;
		this.sequenceDescription = sequenceDescription;

		setImgFactory( imgFactory );
	}

	public File getFile() { return mmFile; }

	@Override
	public RandomAccessibleInterval<FloatType> getFloatImage(ViewId view,
			boolean normalize) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RandomAccessibleInterval<UnsignedShortType> getImage(ViewId view) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void loadMetaData(ViewId view) {
		// TODO Auto-generated method stub
		
	}

}
