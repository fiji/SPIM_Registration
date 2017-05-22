package spim.headless.fusion;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.Threads;
import spim.fiji.spimdata.SpimData2;
import spim.headless.boundingbox.TestBoundingBox;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.transformed.TransformView;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.fusion.transformed.TransformWeight;
import spim.process.fusion.weightedavg.ProcessFusion;
import spim.process.fusion.weightedavg.ProcessVirtual;
import spim.process.fusion.weightedavg.ProcessVirtualPortion;
import spim.process.fusion.weightedavg.ProcessVirtualPortionWeight;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestFusion
{
	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testFusion( spimData );
	}

	public static void testFusion( final SpimData2 spimData )
	{
		Interval bb = TestBoundingBox.testBoundingBox( spimData, false );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// downsampling
		double downsampling = 2; //Double.NaN;

		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( bb, 1.0 / downsampling );

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

		for ( final ViewId viewId : viewIds )
		{
			final ImgLoader imgloader = spimData.getSequenceDescription().getImgLoader();
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();
			AffineTransform3D model = vr.getModel();

			final float[] blending = ProcessFusion.defaultBlendingRange.clone();
			final float[] border = ProcessFusion.defaultBlendingBorder.clone();

			ProcessVirtual.adjustBlending( spimData.getSequenceDescription().getViewDescription( viewId ), blending, border );

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			final RandomAccessibleInterval inputImg = TransformView.openDownsampled( imgloader, viewId, model );

			images.add( TransformView.transformView( inputImg, model, bb, 0, 1 ) );
			weights.add( TransformWeight.transformBlending( inputImg, border, blending, model, bb ) );

			//images.add( TransformWeight.transformBlending( inputImg, border, blending, vr.getModel(), bb ) );
			//weights.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( dim ) ) );
		}

		//
		// display virtually fused
		//
//		DisplayImage.getImagePlusInstance( new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights ), true, "Fused, Virtual", 0, 255 ).show();

		//
		// actually fuse into an image multithreaded
		//
		final long[] size = new long[ bb.numDimensions() ];
		bb.dimensions( size );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused image, size = " + Util.printCoordinates( size ) );

		// try creating the output (type needs to be there to define T)
/*
	 	// copying from FusedRandomAccessibleIntervalas the source
		FusedRandomAccessibleInterval source = new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights );
		DiskCachedCellImgFactory< FloatType > factory = new DiskCachedCellImgFactory<>(
				DiskCachedCellImgOptions.options()
						.cacheType( CacheType.BOUNDED )
						.maxCacheSize( 10 )
						.numIoThreads( 7 )
						.dirtyAccesses( false ) );
		final Img< FloatType > fusedImg = factory.create( Intervals.dimensionsAsLongArray( bb ), new FloatType(), cell ->
		{
			Cursor< FloatType > i = Views.interval( source, cell ).cursor();
			Cursor< FloatType > o = cell.cursor();
			while ( o.hasNext() )
				o.next().set( i.next() );
		} );

		SharedQueue queue = new SharedQueue( 7 );
		BdvFunctions.show( VolatileViews.wrapAsVolatile( fusedImg, queue ), "title" );
*/
	 	// creating empty DiskCachedCellImg and writing to it multithreaded
		DiskCachedCellImgFactory< FloatType > factory = new DiskCachedCellImgFactory<>(
				DiskCachedCellImgOptions.options().cellDimensions( 100 ) );
		final Img< FloatType > fusedImg = factory.create( bb, new FloatType() );
//		final Img< FloatType > fusedImg = new ImagePlusImgFactory< FloatType >().create( bb, new FloatType() );

		if ( fusedImg == null )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): FusionFromVirtual: Cannot create output image."  );
			return;
		}

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( fusedImg.size(), Threads.numThreads() * 4 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< ProcessVirtualPortion< FloatType > > tasks = new ArrayList< ProcessVirtualPortion< FloatType > >();

		for ( final ImagePortion portion : portions )
			tasks.add( new ProcessVirtualPortionWeight< FloatType >( portion, images, weights, fusedImg ) );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Starting fusion process." );

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

		taskExecutor.shutdown();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Finished fusion process." );

		DisplayImage.getImagePlusInstance( fusedImg, true, "Fused", 0, 255 ).show();
	}
}
