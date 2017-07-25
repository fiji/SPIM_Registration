package spim.fiji.spimdata.imgloaders.flatfield;

import java.io.File;

import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

public class MultiResolutionFlatfieldCorrectionWrappedImgLoader extends LazyLoadingFlatFieldCorrectionMap< MultiResolutionImgLoader > implements MultiResolutionImgLoader
{

	private MultiResolutionImgLoader wrappedImgLoader;
	private boolean active;
	
	public MultiResolutionFlatfieldCorrectionWrappedImgLoader(MultiResolutionImgLoader wrappedImgLoader)
	{
		this.wrappedImgLoader = wrappedImgLoader;
		this.active = true;
	}
	
	@Override
	public MultiResolutionSetupImgLoader< ? > getSetupImgLoader(int setupId)
	{
		return new MultiResolutionFlatfieldCorrectionWrappedSetupImgLoader<>();
	}

	@Override
	public MultiResolutionImgLoader getWrappedImgLoder()
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

	class MultiResolutionFlatfieldCorrectionWrappedSetupImgLoader <T extends RealType< T > & NativeType< T >> implements MultiResolutionSetupImgLoader< T >
	{

		@Override
		public RandomAccessibleInterval< T > getImage(int timepointId, int level, ImgLoaderHint... hints)
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public double[][] getMipmapResolutions()
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public AffineTransform3D[] getMipmapTransforms()
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int numMipmapLevels()
		{
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public RandomAccessibleInterval< T > getImage(int timepointId, ImgLoaderHint... hints)
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public T getImageType()
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
				ImgLoaderHint... hints)
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Dimensions getImageSize(int timepointId)
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public VoxelDimensions getVoxelSize(int timepointId)
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, int level, boolean normalize,
				ImgLoaderHint... hints)
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Dimensions getImageSize(int timepointId, int level)
		{
			// TODO Auto-generated method stub
			return null;
		}
		
	}


}
