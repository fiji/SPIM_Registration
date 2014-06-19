package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;

public class ProcessParalell extends ProcessFusion
{	
	public ProcessParalell(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Illumination> illumsToProcess,
			final BoundingBox bb,
			final boolean useBlending,
			final boolean useContentBased )
	{
		super( spimData, anglesToProcess, illumsToProcess, bb, useBlending, useContentBased );
	}

	/** 
	 * Fuses one stack, i.e. all angles/illuminations for one timepoint and channel
	 * 
	 * @param type
	 * @param imgFactoryType
	 * @param timepoint
	 * @param channel
	 * @return
	 */
	@Override
	public < T extends RealType< T > & NativeType< T > > Img< T > fuseStack(
			final T type,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final TimePoint timepoint, 
			final Channel channel )
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused image.");

		// try creating the output (type needs to be there to define T)
		final Img< T > fusedImg = bb.getImgFactory( type ).create( bb.getDimensions(), type );

		if ( fusedImg == null )
		{
			IOFunctions.println( "WeightedAverageFusion: Cannot create output image."  );
			return null;
		}
		
		// get all views that are fused
		final ArrayList< ViewDescription > inputData =
				FusionHelper.assembleInputData( spimData, timepoint, channel, anglesToProcess, illumsToProcess );

		final ArrayList< RandomAccessibleInterval< T > > imgs = new ArrayList< RandomAccessibleInterval< T > >();

		for ( int i = 0; i < inputData.size(); ++i )
			imgs.add( getImage( type, spimData, inputData.get( i ) ) );
		
		// get all weighting methods
		final ArrayList< ArrayList< RealRandomAccessible< FloatType > > > weights = new ArrayList< ArrayList< RealRandomAccessible< FloatType > > >();
		
		for ( int i = 0; i < inputData.size(); ++i )
			weights.add( getAllWeights( imgs.get( i ), inputData.get( i ), spimData.getSequenceDescription().getImgLoader() ) );
		
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( fusedImg.size(), Runtime.getRuntime().availableProcessors() * 4 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
		final ArrayList< ProcessParalellPortion< T > > tasks = new ArrayList< ProcessParalellPortion< T > >();

		if ( weights.get( 0 ).size() == 0 ) // no weights
		{		
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessParalellPortion< T >( portion, imgs, interpolatorFactory, getTransforms( inputData ), fusedImg, bb ) );
		}
		else if ( weights.get( 0 ).size() > 1 ) // many weights
		{
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessParalellPortionWeights< T >( portion, imgs, weights, interpolatorFactory, getTransforms( inputData ), fusedImg, bb ) );
		}
		else // one weight
		{
			final ArrayList< RealRandomAccessible< FloatType > > singleWeight = new ArrayList< RealRandomAccessible< FloatType > >();
			
			for ( int i = 0; i < inputData.size(); ++i )
				singleWeight.add( weights.get( i ).get( 0 ) );
			
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessParalellPortionWeight< T >( portion, imgs, singleWeight, interpolatorFactory, getTransforms( inputData ), fusedImg, bb ) );
		}
		
		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			IOFunctions.println( "Failed to compute fusion: " + e );
			e.printStackTrace();
			return null;
		}

		taskExecutor.shutdown();
		
		return fusedImg;
	}

	@SuppressWarnings("unchecked")
	protected static < T extends RealType< T > > RandomAccessibleInterval< T > getImage( final T type, final SpimData2 spimData, final ViewId view )
	{
		if ( type instanceof FloatType )
			return (RandomAccessibleInterval< T >)(Object)spimData.getSequenceDescription().getImgLoader().getFloatImage( view, false );
		else if ( type instanceof UnsignedShortType )
			return (RandomAccessibleInterval< T >)(Object)spimData.getSequenceDescription().getImgLoader().getImage( view );
		else
			return null;
	}
}
