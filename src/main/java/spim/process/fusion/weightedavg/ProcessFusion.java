package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.List;

import bdv.img.hdf5.Hdf5ImageLoader;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
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
import spim.fiji.ImgLib2Temp;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
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
	final protected List<Angle> anglesToProcess;
	final protected List<Illumination> illumsToProcess;
	final BoundingBox bb;
	final boolean useBlending;
	final boolean useContentBased;
	
	public ProcessFusion(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Illumination> illumsToProcess,
			final BoundingBox bb,
			final boolean useBlending,
			final boolean useContentBased  )
	{
		this.spimData = spimData;
		this.anglesToProcess = anglesToProcess;
		this.illumsToProcess = illumsToProcess;
		this.bb = bb;
		this.useBlending = useBlending;
		this.useContentBased = useContentBased;
	}
	
	public abstract < T extends RealType< T > & NativeType< T > > Img< T > fuseStack(
			final T type,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final TimePoint timepoint, 
			final Channel channel );

	protected Blending getBlending( final Interval interval, final ViewDescription desc, final ImgLoader< ? > imgLoader )
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
	
	protected < T extends RealType< T > > ContentBased< T > getContentBased( final RandomAccessibleInterval< T > img, final ViewDescription desc, final ImgLoader< ? > imgLoader )
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
			final ImgLoader< ? > imgLoader )
	{
		final ArrayList< RealRandomAccessible< FloatType > > weigheners = new ArrayList< RealRandomAccessible< FloatType > >();
		
		if ( useBlending )
		{
			weigheners.add( getBlending( ImgLib2Temp.getIntervalFromDimension( img ), desc, imgLoader ) );
			// TODO: change back to imglib2 implementation once uploaded to Fiji
			//weigheners.add( getBlending( new FinalInterval( img ), desc, imgLoader ) );
		}
		
		if ( useContentBased )
			weigheners.add( getContentBased( img, desc, imgLoader ) );
		
		return weigheners;
	}

	public static double getMinRes( final ViewDescription desc, final ImgLoader< ? > imgLoader )
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

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static < T extends RealType< T > > RandomAccessibleInterval< T > getImage( final T type, final SpimData2 spimData, final ViewId view, final boolean normalize )
	{
		ImgLoader< ? > imgLoader = spimData.getSequenceDescription().getImgLoader();
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
