package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.Threads;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.export.FixedNameImgTitler;
import spim.process.fusion.export.ImgExport;
import spim.process.fusion.export.ImgExportTitle;

/**
 * Fused individual images for each input stack, uses the exporter directly
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ProcessIndependent extends ProcessFusion
{
	final ImgExport export;
	final Map< ViewSetup, ViewSetup > newViewsetups;
	final FixedNameImgTitler titler;
	
	public ProcessIndependent(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final BoundingBoxGUI bb,
			final ImgExport export,
			final Map< ViewSetup, ViewSetup > newViewsetups )
	{
		super( spimData, viewIdsToProcess, bb, false, false );
		
		this.export = export;
		this.newViewsetups = newViewsetups;

		this.titler = new FixedNameImgTitler( "" );
		if ( this.export instanceof ImgExportTitle )
			( (ImgExportTitle)this.export).setImgTitler( titler );

	}

	/** 
	 * Fuses one stack, i.e. all angles/illuminations for one timepoint and channel
	 * 
	 * @param type
	 * @param interpolatorFactory
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
				FusionHelper.assembleInputData( spimData, timepoint, channel, viewIdsToProcess );

		// we will need to run some batches until all is fused
		for ( int i = 0; i < allInputData.size(); ++i )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusing view " + i + " of " + (allInputData.size()-1) );
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused image.");

			// try creating the output (type needs to be there to define T)
			final Img< T > fusedImg = bb.getImgFactory( type ).create( bb.getDimensions(), type );

			if ( fusedImg == null )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): WeightedAverageFusion: Cannot create output image."  );
				return null;
			}
	
			final ViewDescription inputData = allInputData.get( i );
			
			// same as in the paralell fusion now more or less
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Requesting Img from ImgLoader (tp=" + inputData.getTimePointId() + ", setup=" + inputData.getViewSetupId() + ")" );
			final RandomAccessibleInterval< T > img = getImage( type, spimData, inputData, false );
						
			// split up into many parts for multithreading
			final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( fusedImg.size(), Threads.numThreads() * 4 );

			// set up executor service
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
			final ArrayList< ProcessIndependentPortion< T > > tasks = new ArrayList< ProcessIndependentPortion< T > >();

			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessIndependentPortion< T >( portion, img, interpolatorFactory, getTransform( inputData ), fusedImg, bb ) );

			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Starting fusion process.");

			try
			{
				// invokeAll() returns when all tasks are complete
				taskExecutor.invokeAll( tasks );
			}
			catch ( final InterruptedException e )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Failed to compute fusion: " + e );
				e.printStackTrace();
				return null;
			}

			taskExecutor.shutdown();

			titler.setTitle( "TP" + inputData.getTimePointId() + 
					"_Channel" + inputData.getViewSetup().getChannel().getName() +
					"_Illum" + inputData.getViewSetup().getIllumination().getName() +
					"_Angle" + inputData.getViewSetup().getAngle().getName() );
			export.exportImage( fusedImg, bb, timepoint, newViewsetups.get( inputData.getViewSetup() ) );
		}
		
		return null;
	}
	
	protected int numBatches( final int numViews, final int sequentialViews )
	{
		return numViews / sequentialViews + Math.min( numViews % sequentialViews, 1 );
	}
}
