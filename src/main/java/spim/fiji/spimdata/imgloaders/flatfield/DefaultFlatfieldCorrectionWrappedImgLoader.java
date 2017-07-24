package spim.fiji.spimdata.imgloaders.flatfield;

import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class DefaultFlatfieldCorrectionWrappedImgLoader implements ImgLoader, FlatfieldCorrectionWrappedImgLoader< ImgLoader >
{
	private ImgLoader wrappedImgLoader;
	private boolean active;
	
	public DefaultFlatfieldCorrectionWrappedImgLoader(ImgLoader wrappedImgLoader)
	{
		this.wrappedImgLoader = wrappedImgLoader;
		this.active = true;
	}
	
	@Override
	public ImgLoader getWrappedImgLoder()
	{
		return wrappedImgLoader;
	}

	@Override
	public void setActive(boolean active)
	{
		this.active = active;
	}
	
	@Override
	public boolean isActive()
	{
		return active;
	}
	
	@Override
	public SetupImgLoader< ? > getSetupImgLoader(int setupId)
	{
		return new DefaultFlatfieldCorrectionWrappedSetupImgLoader<>(setupId);
	}
	
	class DefaultFlatfieldCorrectionWrappedSetupImgLoader <T extends RealType< T > & NativeType< T >> implements SetupImgLoader< T >
	{
		private final int setupId;
		
		DefaultFlatfieldCorrectionWrappedSetupImgLoader(int setupId)
		{
			this.setupId = setupId;
		}

		@Override
		public RandomAccessibleInterval< T > getImage(int timepointId, ImgLoaderHint... hints)
		{
			// TODO Auto-generated method stub
			return null;
		}

		

		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
				ImgLoaderHint... hints)
		{
			ImagePlus imp = IJ.openImage( "" );
			RandomAccessibleInterval< ? extends NumericType< ? > > wrp = ImageJFunctions.wrap( imp );
			return null;
		}

		@Override
		public T getImageType() { return (T) wrappedImgLoader.getSetupImgLoader( setupId ).getImageType(); }

		@Override
		public Dimensions getImageSize(int timepointId) { return wrappedImgLoader.getSetupImgLoader( setupId ).getImageSize( timepointId ); }

		@Override
		public VoxelDimensions getVoxelSize(int timepointId) { return wrappedImgLoader.getSetupImgLoader( setupId ).getVoxelSize( timepointId ); }
	}

	

}
