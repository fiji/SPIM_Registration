package spim.process.fusion.weightedavg;

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
import bdv.img.hdf5.Hdf5ImageLoader;

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

	
	protected < T extends RealType< T > > ArrayList< RealRandomAccessible< FloatType > > getAllWeights(
			final RandomAccessibleInterval< T > img,
			final ViewDescription desc,
			final ImgLoader< ? > imgLoader )
	{
		final ArrayList< RealRandomAccessible< FloatType > > weigheners = new ArrayList< RealRandomAccessible< FloatType > >();
		
		if ( useBlending )
			weigheners.add( getBlending( new FinalInterval( img ), desc, imgLoader ) );

		if ( useContentBased )
			weigheners.add( getContentBased( img, desc, imgLoader ) );

		return weigheners;
	}



}
