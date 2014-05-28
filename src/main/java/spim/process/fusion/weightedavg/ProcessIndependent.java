package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
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
import spim.process.fusion.export.ImgExport;

/**
 * Fused individual images for each input stack, uses the exporter directly
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ProcessIndependent extends ProcessFusion
{
	final ImgExport export;
	
	public ProcessIndependent(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final BoundingBox bb,
			final ImgExport export )
	{
		super( spimData, anglesToProcess, illumsToProcess, bb, false, false );
		
		this.export = export;
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
		// get all views that are fused
		final ArrayList< ViewDescription > allInputData =
				FusionHelper.assembleInputData( spimData, timepoint, channel, anglesToProcess, illumsToProcess );
		
		// we will need to run some batches until all is fused
		for ( int i = 0; i < allInputData.size(); ++i )
		{
			IOFunctions.println( "Fusing view " + i + " of " + (allInputData.size()-1) );
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused image.");

			// try creating the output (type needs to be there to define T)
			final Img< T > fusedImg = bb.getImgFactory( type ).create( bb.getDimensions(), type );

			if ( fusedImg == null )
			{
				IOFunctions.println( "WeightedAverageFusion: Cannot create output image."  );
				return null;
			}
	
			final ViewDescription inputData = allInputData.get( i );
			
			// same as in the paralell fusion now more or less
			final RandomAccessibleInterval< T > img = getImage( type, spimData, inputData );
						
			// split up into many parts for multithreading
			final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( fusedImg.size(), Runtime.getRuntime().availableProcessors() * 4 );

			// set up executor service
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
			final ArrayList< ProcessIndependentPortion< T > > tasks = new ArrayList< ProcessIndependentPortion< T > >();

			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessIndependentPortion< T >( portion, img, interpolatorFactory, getTransform( inputData ), fusedImg, bb ) );
			
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
			
			export.exportImage( fusedImg, bb, "TP" + timepoint.getName() + "_Ch" + channel.getName() + 
					"_Angle" + inputData.getViewSetup().getAngle().getName() +
					"_Illum" + inputData.getViewSetup().getIllumination().getName() );
		}
		
		return null;
	}
	
	protected int numBatches( final int numViews, final int sequentialViews )
	{
		return numViews / sequentialViews + Math.min( numViews % sequentialViews, 1 );
	}

	@SuppressWarnings("unchecked")
	protected static < T extends RealType< T > > RandomAccessibleInterval< T > getImage( final T type, final SpimData2 spimData, final ViewDescription view )
	{
		if ( type instanceof FloatType )
			return (RandomAccessibleInterval< T >)(Object)spimData.getSequenceDescription().getImgLoader().getImage( view, false );
		else if ( type instanceof UnsignedShortType )
			return (RandomAccessibleInterval< T >)(Object)spimData.getSequenceDescription().getImgLoader().getUnsignedShortImage( view );
		else
			return null;
	}
}
