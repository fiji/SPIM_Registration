package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import spim.Threads;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.headless.fusion.FusionTools;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.boundingbox.BoundingBoxGUI;

/**
 * Fused individual images for each input stack, uses the exporter directly
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class TransformView
{
	public static < T extends RealType< T > & NativeType< T > > Img< T > render(
			final T type,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final RandomAccessibleInterval< T > input,
			final AffineTransform3D model,
			final ImgFactory< T > factory,
			final BoundingBoxGUI bb,
			final int downsampling,
			final ExecutorService exec )
	{
		// try creating the output (type needs to be there to define T)
		final Img< T > fusedImg = factory.create( bb.getDimensions( downsampling ), type );

		render( type, interpolatorFactory, input, fusedImg, model, bb, downsampling, exec );

		return fusedImg;
	}

	/** 
	 * Transforms one stack
	 */
	public static < T extends RealType< T > & NativeType< T > > void render(
			final T type,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final RandomAccessibleInterval< T > input,
			final RandomAccessibleInterval< T > output,
			final AffineTransform3D model,
			final BoundingBox bb,
			final int downsampling,
			final ExecutorService exec )
	{
		if ( !FusionTools.matches( output, bb, downsampling ) )
			throw new RuntimeException( "Output RAI does not match BoundingBox with downsampling" );

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions(
				Views.iterable( output ).size(), Threads.numThreads() * 4 );

		// set up executor service
		final ExecutorService taskExecutor;

		if ( exec == null )
			taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		else
			taskExecutor = exec;

		final ArrayList< ProcessIndependentPortion< T > > tasks = new ArrayList< ProcessIndependentPortion< T > >();

		for ( final ImagePortion portion : portions )
			tasks.add( new ProcessIndependentPortion< T >( portion, input, interpolatorFactory, model, output, bb, downsampling ) );

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
			return;
		}

		if ( exec == null )
			taskExecutor.shutdown();

		/*titler.setTitle( "TP" + inputData.getTimePointId() + 
					"_Channel" + inputData.getViewSetup().getChannel().getName() +
					"_Illum" + inputData.getViewSetup().getIllumination().getName() +
					"_Angle" + inputData.getViewSetup().getAngle().getName() );
			export.exportImage( fusedImg, bb, timepoint, newViewsetups.get( inputData.getViewSetup() ) );
		*/

		return;
	}
}
