package spim.process.fusion.weightedavg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import spim.Threads;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.transformed.TransformView;
import spim.process.fusion.transformed.TransformedInputRandomAccessible;
import spim.process.fusion.transformed.TransformedRasteredRealRandomAccessible;
import spim.process.fusion.transformed.weights.BlendingRealRandomAccessible;
import spim.process.fusion.transformed.weights.ContentBasedRealRandomAccessible;

public class ProcessVirtual extends ProcessFusion
{
	final int interpolation;

	public ProcessVirtual(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final Interval boundingBox,
			final int downSampling,
			final int interpolation,
			final boolean useBlending,
			final boolean useContentBased )
	{
		super( spimData, viewIdsToProcess, boundingBox, downSampling, useBlending, useContentBased );

		this.interpolation = interpolation;
	}

	/** 
	 * Fuses one stack, i.e. all angles/illuminations for one timepoint and channel
	 */
	@Override
	public < T extends RealType< T > & NativeType< T > > Img< T > fuseStack(
			final T type,
			final TimePoint timepoint,
			final Channel channel,
			final ImgFactory< T > imgFactory )
	{
		// get all views that are fused
		final ArrayList< ViewDescription > inputData = FusionHelper.assembleInputData( spimData, timepoint, channel, viewIdsToProcess );

		// it can be that for a certain comination of timepoint/channel there is nothing to do
		// (e.g. fuse timepoint 1 channel 1 and timepoint 2 channel 2)
		if ( inputData.size() == 0 )
			return null;

		// update bounding box if necessary for downsampling
		final Interval bb;

		if ( downSampling == 1 )
			bb = boundingBox;
		else
			bb = TransformView.scaleBoundingBox( boundingBox, downSampling );

		final long[] size = new long[ bb.numDimensions() ];
		bb.dimensions( size );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused image, size = " + Util.printCoordinates( size ) );

		// try creating the output (type needs to be there to define T)
		final Img< T > fusedImg = imgFactory.create( bb, type );

		if ( fusedImg == null )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): FusionFromVirtual: Cannot create output image."  );
			return null;
		}

		// define the output
		final Interval outputInterval = new FinalInterval( size );
		final long[] offset = new long[]{ bb.min( 0 ), bb.min( 1 ), bb.min( 2 ) };

		// list of transformed input images and weights
		final ArrayList< RandomAccessibleInterval > originalImgs = new ArrayList< RandomAccessibleInterval >();

		final ArrayList< RandomAccessibleInterval< FloatType > > transformedImgs = new ArrayList< RandomAccessibleInterval< FloatType > >();
		final ArrayList< ArrayList< RandomAccessibleInterval< FloatType > > > transformedWeights = new ArrayList< ArrayList< RandomAccessibleInterval< FloatType > > >();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up " + inputData.size() + " input images." );

		for ( int i = 0; i < inputData.size(); ++i )
		{
			final ViewDescription vd = inputData.get( i );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Requesting Img from ImgLoader (tp=" + vd.getTimePointId() + ", setup=" + vd.getViewSetupId() + ") using " + spimData.getSequenceDescription().getImgLoader().getClass().getSimpleName() );

			final Object inputType = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( vd.getViewSetupId() ).getImageType();
			
			if ( !RealType.class.isInstance( type ) )
				throw new RuntimeException( "Cannot load image, type == " + type.getClass().getSimpleName() );
			else
				IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Image Type = " + type.getClass().getSimpleName() );

			final Interval inputImgInterval;
			final RandomAccessibleInterval< ? > inputImg = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( vd.getViewSetupId() ).getImage( vd.getTimePointId() );

			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Input image class: " + inputImg.getClass().getSimpleName() );
			
			if ( Img.class.isInstance( inputImg ) )
			{
				if ( ((Img< ? >)inputImg).factory() == null )
					IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Input image factory: NULL" );
				else
					IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Input image factory: " + ((Img< ? >)inputImg).factory().getClass().getSimpleName() );
			}

			inputImgInterval = new FinalInterval( inputImg );

			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Image dimensions: " + inputImgInterval.dimension( 0 ) + "x" + inputImgInterval.dimension( 1 ) + "x" + inputImgInterval.dimension( 2 ) + " px." );
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Initializing virtual transformation of image" );

			spimData.getViewRegistrations().getViewRegistration( vd ).updateModel();
			final AffineTransform3D transform = spimData.getViewRegistrations().getViewRegistration( vd ).getModel().copy();

			if ( downSampling > 1 )
			{
				TransformView.scaleTransform( transform,  1.0 / downSampling );
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Applying downsampling: " + downSampling );
			}

			// values outside of the image area are -1
			final RandomAccessible< FloatType > virtual = new TransformedInputRandomAccessible( inputImg, transform, false, 0.0f, new FloatType( -1 ), offset );

			if ( interpolation == 0 )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting nearest-neigbor interpolation" );
				((TransformedInputRandomAccessible<?>)virtual).setNearestNeighborInterpolation();
			}
			else
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting linear interpolation" );
				((TransformedInputRandomAccessible<?>)virtual).setLinearInterpolation();
			}

			final RandomAccessibleInterval< FloatType > virtualInterval = Views.interval( virtual, outputInterval );

			transformedImgs.add( virtualInterval );
			originalImgs.add( inputImg );

			//
			// add blending
			//
			final ArrayList< RandomAccessibleInterval< FloatType > > weightsPerView = new ArrayList< RandomAccessibleInterval< FloatType > >();

			if ( useBlending )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up blending weights virtually" );

				final float[] blending = ProcessFusion.defaultBlendingRange.clone();
				final float[] border = ProcessFusion.defaultBlendingBorder.clone();

				final float minRes = (float)getMinRes( vd );
				VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSize( vd.getViewSetup() );
				if ( voxelSize == null )
					voxelSize = new FinalVoxelDimensions( "px", new double[]{ 1, 1, 1 } );

				if ( ProcessFusion.defaultAdjustBlendingForAnisotropy )
				{
					for ( int d = 0; d < inputImg.numDimensions(); ++d )
					{
						blending[ d ] /= ( float ) voxelSize.dimension( d ) / minRes;
						border[ d ] /= ( float ) voxelSize.dimension( d ) / minRes;
					}
				}

				// the virtual weight construct
				final RandomAccessible< FloatType > virtualBlending =
						new TransformedRasteredRealRandomAccessible< FloatType >(
							new BlendingRealRandomAccessible( inputImgInterval, border, blending ),
							new FloatType(),
							transform,
							offset );
				final RandomAccessibleInterval< FloatType > virtualBlendingInterval = Views.interval( virtualBlending, outputInterval );

				weightsPerView.add( virtualBlendingInterval );
			}

			if ( useContentBased )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up content-based weights (slow!!)" );

				final double[] sigma1 = ProcessFusion.defaultContentBasedSigma1.clone();
				final double[] sigma2 = ProcessFusion.defaultContentBasedSigma2.clone();

				final float minRes = (float)getMinRes( vd );
				VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSize( vd.getViewSetup() );
				if ( voxelSize == null )
					voxelSize = new FinalVoxelDimensions( "px", new double[]{ 1, 1, 1 } );

				if ( ProcessFusion.defaultAdjustContentBasedSigmaForAnisotropy )
				{
					for ( int d = 0; d < inputImg.numDimensions(); ++d )
					{
						sigma1[ d ] /= voxelSize.dimension( d ) / minRes;
						sigma2[ d ] /= voxelSize.dimension( d ) / minRes;
					}
				}

				try
				{
					RandomAccessible< FloatType > virtualContent = new TransformedRasteredRealRandomAccessible< FloatType >(
						new ContentBasedRealRandomAccessible( inputImg, imgFactory.imgFactory( new ComplexFloatType() ), sigma1, sigma2 ),
						new FloatType(),
						transform,
						offset );

					final RandomAccessibleInterval< FloatType > virtualContentInterval = Views.interval( virtualContent, outputInterval );

					weightsPerView.add( virtualContentInterval );
				}
				catch ( Exception e )
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Cannot compute content-based fusion: " + e );
				}
			}

			transformedWeights.add( weightsPerView );
		}

		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( fusedImg.size(), Threads.numThreads() * 4 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< ProcessVirtualPortion< T > > tasks = new ArrayList< ProcessVirtualPortion< T > >();

		if ( transformedWeights.get( 0 ).size() == 0 ) // no weights
		{		
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessVirtualPortion< T >( portion, transformedImgs, fusedImg ) );
		}
		else if ( transformedWeights.get( 0 ).size() > 1 ) // many weights
		{
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessVirtualPortionWeights< T >( portion, transformedImgs, transformedWeights, fusedImg ) );
		}
		else // one weight
		{
			final ArrayList< RandomAccessibleInterval< FloatType > > singleWeightPerView = new ArrayList< RandomAccessibleInterval< FloatType > >();
			
			for ( int i = 0; i < inputData.size(); ++i )
				singleWeightPerView.add( transformedWeights.get( i ).get( 0 ) );
			
			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessVirtualPortionWeight< T >( portion, transformedImgs, singleWeightPerView, fusedImg ) );
		}

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
			return null;
		}

		taskExecutor.shutdown();
		
		return fusedImg;
	}

	public static double getMinRes( final ViewDescription desc )
	{
		final VoxelDimensions size = ViewSetupUtils.getVoxelSize( desc.getViewSetup() );

		if ( size == null )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): WARNINIG, could not load voxel size!! Assuming 1,1,1"  );
			return 1;
		}

		return Math.min( size.dimension( 0 ), Math.min( size.dimension( 1 ), size.dimension( 2 ) ) );
	}

}
