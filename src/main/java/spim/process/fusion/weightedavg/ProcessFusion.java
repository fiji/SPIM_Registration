package spim.process.fusion.weightedavg;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
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
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.spimdata.SpimData2;
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
	final protected ArrayList<Angle> anglesToProcess;
	final protected ArrayList<Illumination> illumsToProcess;
	final BoundingBox bb;
	final boolean useBlending;
	final boolean useContentBased;
	
	public ProcessFusion(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Illumination> illumsToProcess,
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

	protected Blending getBlending( final Interval interval, final ViewDescription< TimePoint, ViewSetup > desc )
	{
		final float[] blending = ProcessFusion.defaultBlendingRange.clone();
		final float[] border = ProcessFusion.defaultBlendingBorder.clone();
		
		final float minRes = (float)getMinRes( desc.getViewSetup() );
		
		if ( ProcessFusion.defaultAdjustBlendingForAnisotropy )
		{
			blending[ 0 ] /= (float)desc.getViewSetup().getPixelWidth() / minRes;
			blending[ 1 ] /= (float)desc.getViewSetup().getPixelHeight() / minRes;
			blending[ 2 ] /= (float)desc.getViewSetup().getPixelDepth() / minRes;

			border[ 0 ] /= (float)desc.getViewSetup().getPixelWidth() / minRes;
			border[ 1 ] /= (float)desc.getViewSetup().getPixelHeight() / minRes;
			border[ 2 ] /= (float)desc.getViewSetup().getPixelDepth() / minRes;
		}
		
		return new Blending( interval, border, blending );
	}
	
	protected < T extends RealType< T > > ContentBased< T > getContentBased( final RandomAccessibleInterval< T > img, final ViewDescription< TimePoint, ViewSetup > desc )
	{
		final double[] sigma1 = ProcessFusion.defaultContentBasedSigma1.clone();
		final double[] sigma2 = ProcessFusion.defaultContentBasedSigma2.clone();

		final double minRes = getMinRes( desc.getViewSetup() );

		if ( ProcessFusion.defaultAdjustContentBasedSigmaForAnisotropy )
		{
			sigma1[ 0 ] /= desc.getViewSetup().getPixelWidth() / minRes;
			sigma1[ 1 ] /= desc.getViewSetup().getPixelHeight() / minRes;
			sigma1[ 2 ] /= desc.getViewSetup().getPixelDepth() / minRes;

			sigma2[ 0 ] /= desc.getViewSetup().getPixelWidth() / minRes;
			sigma2[ 1 ] /= desc.getViewSetup().getPixelHeight() / minRes;
			sigma2[ 2 ] /= desc.getViewSetup().getPixelDepth() / minRes;
		}

		return new ContentBased<T>( img, bb.getImgFactory( new ComplexFloatType() ), sigma1, sigma2);
	}
	
	protected < T extends RealType< T > > ArrayList< RealRandomAccessible< FloatType > > getAllWeights(
			final RandomAccessibleInterval< T > img,
			final ViewDescription< TimePoint, ViewSetup > desc )
	{
		final ArrayList< RealRandomAccessible< FloatType > > weigheners = new ArrayList< RealRandomAccessible< FloatType > >();
		
		if ( useBlending )
			weigheners.add( getBlending( new FinalInterval( img ), desc ) );
		
		if ( useContentBased )
			weigheners.add( getContentBased( img, desc ) );
		
		return weigheners;
	}

	public static double getMinRes( final ViewSetup setup )
	{
		return Math.min( setup.getPixelWidth(), Math.min( setup.getPixelHeight(), setup.getPixelDepth() ) );
	}

	protected AffineTransform3D getTransform( final ViewDescription< TimePoint, ViewSetup > inputData )
	{
		return spimData.getViewRegistrations().getViewRegistration( inputData ).getModel();
	}

	protected AffineTransform3D[] getTransforms( final ArrayList< ViewDescription< TimePoint, ViewSetup > > inputData )
	{
		final int numViews = inputData.size();
		final AffineTransform3D[] transforms = new AffineTransform3D[ numViews ];

		for ( int i = 0; i < numViews; ++i )
			transforms[ i ] = getTransform( inputData.get( i ) );
		
		return transforms;
	}

}
