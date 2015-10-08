package spim.process.fusion.weightedavg;

import static mpicbg.spim.data.generic.sequence.ImgLoaderHints.LOAD_COMPLETELY;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.weights.Blending;
import spim.process.fusion.weights.ContentBased;

public abstract class ProcessFusion
{
	public static float[] defaultBlendingRange = new float[]{ 40, 40, 40 };
	public static float[] defaultBlendingBorder = new float[]{ 0, 0, 0 };
	public static boolean defaultAdjustBlendingForAnisotropy = true;
	
	public static double[] defaultContentBasedSigma1 = new double[]{ 20, 20, 20 };
	public static double[] defaultContentBasedSigma2 = new double[]{ 40, 40, 40 };
	public static boolean defaultAdjustContentBasedSigmaForAnisotropy = true;
	
	final protected SpimData2 spimData;
	final List< ViewId > viewIdsToProcess;
	final BoundingBoxGUI bb;
	final boolean useBlending;
	final boolean useContentBased;
	
	public ProcessFusion(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final BoundingBoxGUI bb,
			final boolean useBlending,
			final boolean useContentBased  )
	{
		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
		this.bb = bb;
		this.useBlending = useBlending;
		this.useContentBased = useContentBased;
	}
	
	public abstract < T extends RealType< T > & NativeType< T > > Img< T > fuseStack(
			final T type,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final TimePoint timepoint, 
			final Channel channel );

	protected Blending getBlending( final Interval interval, final ViewDescription desc, final ImgLoader imgLoader )
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

	protected < T extends RealType< T > > ContentBased< T > getContentBased( final RandomAccessibleInterval< T > img, final ViewDescription desc, final ImgLoader imgLoader )
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
	
	protected < T extends RealType< T > > ArrayList< RealRandomAccessible< FloatType > > getAllWeights(
			final RandomAccessibleInterval< T > img,
			final ViewDescription desc,
			final ImgLoader imgLoader )
	{
		final ArrayList< RealRandomAccessible< FloatType > > weigheners = new ArrayList< RealRandomAccessible< FloatType > >();
		
		if ( useBlending )
			weigheners.add( getBlending( new FinalInterval( img ), desc, imgLoader ) );

		if ( useContentBased )
			weigheners.add( getContentBased( img, desc, imgLoader ) );

		return weigheners;
	}

	public static double getMinRes( final ViewDescription desc, final ImgLoader imgLoader )
	{
		final VoxelDimensions size = ViewSetupUtils.getVoxelSizeOrLoad( desc.getViewSetup(), desc.getTimePoint(), imgLoader );
		return Math.min( size.dimension( 0 ), Math.min( size.dimension( 1 ), size.dimension( 2 ) ) );
	}

	protected AffineTransform3D getTransform( final ViewDescription inputData )
	{
		return spimData.getViewRegistrations().getViewRegistration( inputData ).getModel();
	}

	protected AffineTransform3D[] getTransforms( final ArrayList< ViewDescription > inputData )
	{
		final int numViews = inputData.size();
		final AffineTransform3D[] transforms = new AffineTransform3D[ numViews ];

		for ( int i = 0; i < numViews; ++i )
			transforms[ i ] = getTransform( inputData.get( i ) );
		
		return transforms;
	}

	public static < T extends RealType< T > > RandomAccessibleInterval< T > getImage( final T type, final SpimData2 spimData, final ViewId view, final boolean normalize )
	{
		return getImage( type, spimData.getSequenceDescription().getImgLoader(), view, normalize );
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static < T extends RealType< T > > RandomAccessibleInterval< T > getImage( final T type, ImgLoader imgLoader, final ViewId view, final boolean normalize )
	{
		if ( (RealType)type instanceof FloatType )
			return (RandomAccessibleInterval)imgLoader.getSetupImgLoader( view.getViewSetupId() ).getFloatImage( view.getTimePointId(), normalize, LOAD_COMPLETELY );
		else if ( (RealType)type instanceof UnsignedShortType )
			return (RandomAccessibleInterval)imgLoader.getSetupImgLoader( view.getViewSetupId() ).getImage( view.getTimePointId(), LOAD_COMPLETELY );
		else
			return null;
	}
}
