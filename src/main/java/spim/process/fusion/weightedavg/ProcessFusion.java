package spim.process.fusion.weightedavg;

import java.util.List;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.spimdata.SpimData2;

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
	final Interval boundingBox;
	final int downSampling;
	final boolean useBlending;
	final boolean useContentBased;
	
	public ProcessFusion(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final Interval bb,
			final int downSampling,
			final boolean useBlending,
			final boolean useContentBased  )
	{
		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
		this.boundingBox = bb;
		this.downSampling = downSampling;
		this.useBlending = useBlending;
		this.useContentBased = useContentBased;
	}
	
	public abstract < T extends RealType< T > & NativeType< T > > Img< T > fuseStack(
			final T type,
			final TimePoint timepoint, 
			final Channel channel,
			final ImgFactory< T > imgFactory );
}
