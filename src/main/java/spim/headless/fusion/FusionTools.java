package spim.headless.fusion;

import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.fusion.weightedavg.ProcessFusion;
import spim.process.fusion.weights.Blending;
import spim.process.fusion.weights.ContentBased;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import bdv.img.hdf5.Hdf5ImageLoader;

public class FusionTools
{
	public static Blending getBlending( final Interval interval, final ViewDescription desc, final ImgLoader< ? > imgLoader )
	{
		final float[] blending = ProcessFusion.defaultBlendingRange.clone();
		final float[] border = ProcessFusion.defaultBlendingBorder.clone();
		
		final float minRes = (float)getMinRes( desc, imgLoader );
		final VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSizeOrLoad( desc.getViewSetup(), desc.getTimePoint(), imgLoader );

		if ( ProcessFusion.defaultAdjustBlendingForAnisotropy )
		{
			for ( int d = 0; d < 2; ++d )
			{
				blending[ d ] /= ( float ) voxelSize.dimension( d ) / minRes;
				border[ d ] /= ( float ) voxelSize.dimension( d ) / minRes;
			}
		}
		
		return new Blending( interval, border, blending );
	}
	
	public static < T extends RealType< T > > ContentBased< T > getContentBased( final RandomAccessibleInterval< T > img, final ViewDescription desc, final ImgLoader< ? > imgLoader )
	{
		final double[] sigma1 = ProcessFusion.defaultContentBasedSigma1.clone();
		final double[] sigma2 = ProcessFusion.defaultContentBasedSigma2.clone();

		final double minRes = getMinRes( desc, imgLoader );
		final VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSizeOrLoad( desc.getViewSetup(), desc.getTimePoint(), imgLoader );

		if ( ProcessFusion.defaultAdjustContentBasedSigmaForAnisotropy )
		{
			for ( int d = 0; d < 2; ++d )
			{
				sigma1[ d ] /= voxelSize.dimension( d ) / minRes;
				sigma2[ d ] /= voxelSize.dimension( d ) / minRes;
			}
		}

		return new ContentBased<T>( img, bb.getImgFactory( new ComplexFloatType() ), sigma1, sigma2);
	}

	public static boolean matches( final Interval interval, final BoundingBox bb, final int downsampling )
	{
		for ( int d = 0; d < interval.numDimensions(); ++d )
			if ( interval.dimension( d ) != bb.getDimensions( downsampling )[ d ] )
				return false;

		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static < T extends RealType< T > > RandomAccessibleInterval< T > getImage(
			final T type,
			ImgLoader< ? > imgLoader,
			final ViewId view,
			final boolean normalize )
	{
		if ( imgLoader instanceof Hdf5ImageLoader )
			imgLoader = ( ( Hdf5ImageLoader ) imgLoader ).getMonolithicImageLoader();

		if ( (RealType)type instanceof FloatType )
			return (RandomAccessibleInterval)imgLoader.getFloatImage( view, normalize );
		else if ( (RealType)type instanceof UnsignedShortType )
			return (RandomAccessibleInterval)imgLoader.getImage( view );
		else
			return null;
	}

}
