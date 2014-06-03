package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
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

public class ProcessSequential extends ProcessFusion
{
	final int numSequentialViews;
	
	public ProcessSequential(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final BoundingBox bb,
			final boolean useBlending,
			final boolean useContentBased,
			final int numSequentialViews )
	{
		super( spimData, anglesToProcess, illumsToProcess, bb, useBlending, useContentBased );
		
		this.numSequentialViews = numSequentialViews;
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
		
		// create the image for the weights
		final Img< FloatType > weightImg = bb.getImgFactory( new FloatType() ).create( bb.getDimensions(), new FloatType() );

		if ( weightImg == null )
		{
			IOFunctions.println( "WeightedAverageFusion: Cannot create weight image."  );
			return null;
		}
		
		// get all views that are fused
		final ArrayList< ViewDescription > allInputData =
				FusionHelper.assembleInputData( spimData, timepoint, channel, anglesToProcess, illumsToProcess );
		
		// we will need to run some batches until all is fused
		for ( int batch = 0; batch < numBatches( allInputData.size(), numSequentialViews ); ++batch )
		{
			final int start = batch * numSequentialViews;
			final int end = Math.min( ( batch + 1 ) * numSequentialViews, allInputData.size() );
			
			IOFunctions.println( "Fusing view " + start + " ... " + (end-1) + " of " + (allInputData.size()-1) );
			
			final ArrayList< ViewDescription > inputData = new ArrayList< ViewDescription >();
			
			for ( int i = start; i < end; ++i )
				inputData.add( allInputData.get( i ) );
			
			// same as in the paralell fusion now more or less
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
			final ArrayList< ProcessSequentialPortion< T > > tasks = new ArrayList< ProcessSequentialPortion< T > >();

			if ( weights.get( 0 ).size() == 0 ) // no weights
			{		
				for ( final ImagePortion portion : portions )
					tasks.add( new ProcessSequentialPortion< T >( portion, imgs, interpolatorFactory, getTransforms( inputData ), fusedImg, weightImg, bb ) );
			}
			else if ( weights.get( 0 ).size() > 1 ) // many weights
			{
				for ( final ImagePortion portion : portions )
					tasks.add( new ProcessSequentialPortionWeights< T >( portion, imgs, weights, interpolatorFactory, getTransforms( inputData ), fusedImg, weightImg, bb ) );
			}
			else // one weight
			{
				final ArrayList< RealRandomAccessible< FloatType > > singleWeight = new ArrayList< RealRandomAccessible< FloatType > >();
				
				for ( int i = 0; i < inputData.size(); ++i )
					singleWeight.add( weights.get( i ).get( 0 ) );
				
				for ( final ImagePortion portion : portions )
					tasks.add( new ProcessSequentialPortionWeight< T >( portion, imgs, singleWeight, interpolatorFactory, getTransforms( inputData ), fusedImg, weightImg, bb ) );
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
		}

		// compute final image from intensities and weights
		mergeFinalImage( fusedImg, weightImg );
		
		return fusedImg;
	}
	
	protected < T extends RealType< T > > void mergeFinalImage( final Img< T > img, final Img< FloatType > weights )
	{
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( img.size(), Runtime.getRuntime().availableProcessors() * 4 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
		final ArrayList< Callable< String > > tasks = new ArrayList< Callable< String > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< String >() 
					{
						@Override
						public String call() throws Exception
						{
							final Cursor< T > cursor = img.cursor();
							final Cursor< FloatType > cursorW = weights.cursor();
							
							cursor.jumpFwd( portion.getStartPosition() );
							cursorW.jumpFwd( portion.getStartPosition() );
							
							for ( int j = 0; j < portion.getLoopSize(); ++j )
							{
								final float w = cursorW.next().get();
								final T type = cursor.next();

								if ( w > 0 )
									type.setReal( type.getRealFloat() / w );
							}
							
							return "";
						}
					});
		}
		
		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			IOFunctions.println( "Failed to merge final image: " + e );
			e.printStackTrace();
			return;
		}

		taskExecutor.shutdown();
		
	}
	
	protected int numBatches( final int numViews, final int sequentialViews )
	{
		return numViews / sequentialViews + Math.min( numViews % sequentialViews, 1 );
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
