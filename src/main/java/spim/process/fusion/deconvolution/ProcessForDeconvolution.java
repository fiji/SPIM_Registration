package spim.process.fusion.deconvolution;

import ij.ImagePlus;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.Threads;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.imgloaders.LegacyStackImgLoaderIJ;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.weightedavg.ProcessFusion;
import spim.process.fusion.weights.Blending;
import spim.process.fusion.weights.NormalizingRandomAccessibleInterval;
import spim.process.fusion.weights.TransformedRealRandomAccessibleInterval;
import bdv.util.ConstantRandomAccessible;

/**
 * Fused individual images for each input stack, uses the exporter directly
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ProcessForDeconvolution
{
	public static enum WeightType { WEIGHTS_ONLY, NO_WEIGHTS, VIRTUAL_WEIGHTS, PRECOMPUTED_WEIGHTS, LOAD_WEIGHTS };

	final protected SpimData2 spimData;
	final protected List< ViewId > viewIdsToProcess;
	final BoundingBoxGUI bb;
	final int[] blendingBorder;
	final int[] blendingRange;
	
	int minOverlappingViews;
	double avgOverlappingViews;
	ArrayList< ViewDescription > viewDescriptions;
	HashMap< ViewId, RandomAccessibleInterval< FloatType > > imgs, weights;
	ExtractPSF< FloatType > ePSF;

	public static String[] files;
	public static boolean debugImport = false;

	public ProcessForDeconvolution(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final BoundingBoxGUI bb,
			final int[] blendingBorder,
			final int[] blendingRange )
	{
		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
		this.bb = bb;
		this.blendingBorder = blendingBorder;
		this.blendingRange = blendingRange;
	}
	
	public ExtractPSF< FloatType > getExtractPSF() { return ePSF; }
	public HashMap< ViewId, RandomAccessibleInterval< FloatType > > getTransformedImgs() { return imgs; }
	public HashMap< ViewId, RandomAccessibleInterval< FloatType > > getTransformedWeights() { return weights; }
	public ArrayList< ViewDescription > getViewDescriptions() { return viewDescriptions; }
	public int getMinOverlappingViews() { return minOverlappingViews; }
	public double getAvgOverlappingViews() { return avgOverlappingViews; }

	/** 
	 * Fuses one stack, i.e. all angles/illuminations for one timepoint and channel
	 * 
	 * @param timepoint
	 * @param channel
	 * @return
	 */
	public boolean fuseStacksAndGetPSFs(
			final TimePoint timepoint, 
			final Channel channel,
			final ImgFactory< FloatType > imgFactory,
			final int osemIndex,
			double osemspeedup,
			WeightType weightType,
			final HashMap< Channel, ChannelPSF > extractPSFLabels,
			final long[] psfSize,
			final HashMap< Channel, ArrayList< Pair< Pair< Angle, Illumination >, String > > > psfFiles,
			final boolean transformLoadedPSFs )
	{
		// TODO: get rid of this hack
		if ( files != null )
		{
			weightType = WeightType.LOAD_WEIGHTS;
			IOFunctions.println( "WARNING: LOADING WEIGHTS FROM IMAGES, files.length()=" + files.length );
		}

		// get all views that are fused for this timepoint & channel
		this.viewDescriptions = FusionHelper.assembleInputData( spimData, timepoint, channel, viewIdsToProcess );

		if ( this.viewDescriptions.size() == 0 )
			return false;

		this.imgs = new HashMap< ViewId, RandomAccessibleInterval< FloatType > >();
		this.weights = new HashMap< ViewId, RandomAccessibleInterval< FloatType > >();

		final Img< FloatType > overlapImg;

		if ( weightType == WeightType.WEIGHTS_ONLY )
			overlapImg = imgFactory.create( bb.getDimensions(), new FloatType() );
		else
			overlapImg = null;

		final boolean extractPSFs = (extractPSFLabels != null) && (extractPSFLabels.get( channel ).getLabel() != null);
		final boolean loadPSFs = (psfFiles != null);

		if ( extractPSFs )
			ePSF = new ExtractPSF< FloatType >();
		else if ( loadPSFs )
			ePSF = loadPSFs( channel, viewDescriptions, psfFiles, transformLoadedPSFs );
		else
		{
			ePSF = assignOtherChannel( channel, extractPSFLabels );
		}

		if ( ePSF == null )
			return false;

		// remember the extracted or loaded PSFs
		extractPSFLabels.get( channel ).setExtractPSFInstance( ePSF );

		// we will need to run some batches until all is fused
		for ( int i = 0; i < viewDescriptions.size(); ++i )
		{
			final ViewDescription vd = viewDescriptions.get( i );

			IOFunctions.println( "Transforming view " + i + " of " + (viewDescriptions.size()-1) + " (viewsetup=" + vd.getViewSetupId() + ", tp=" + vd.getTimePointId() + ")" );
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for transformed & weight image.");

			// creating the output
			RandomAccessibleInterval< FloatType > transformedImg; // might be null if WEIGHTS_ONLY
			final RandomAccessibleInterval< FloatType > weightImg; // never null (except LOAD_WEIGHTS which is not implemented yet)

			if ( weightType == WeightType.WEIGHTS_ONLY )
				transformedImg = overlapImg;
			else
				transformedImg = imgFactory.create( bb.getDimensions(), new FloatType() );

			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Transformed image factory: " + imgFactory.getClass().getSimpleName() );

			// loading the input if necessary
			final RandomAccessibleInterval< FloatType > img;

			if ( weightType == WeightType.WEIGHTS_ONLY && !extractPSFs )
			{
				img = null;
			}
			else
			{
				IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Loading image.");
				img = ProcessFusion.getImage( new FloatType(), spimData, vd, true );

				if ( Img.class.isInstance( img ) )
					IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Input image factory: " + ((Img< FloatType >)img).factory().getClass().getSimpleName() );
			}

			// initializing weights
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Initializing transformation & weights: " + weightType.name() );

			spimData.getViewRegistrations().getViewRegistration( vd ).updateModel();
			final AffineTransform3D transform = spimData.getViewRegistrations().getViewRegistration( vd ).getModel();
			final long[] offset = new long[]{ bb.min( 0 ), bb.min( 1 ), bb.min( 2 ) };

			if ( weightType == WeightType.PRECOMPUTED_WEIGHTS || weightType == WeightType.WEIGHTS_ONLY )
				weightImg = imgFactory.create( bb.getDimensions(), new FloatType() );
			else if ( weightType == WeightType.NO_WEIGHTS )
				weightImg = Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), transformedImg.numDimensions() ), transformedImg );
			else if ( weightType == WeightType.VIRTUAL_WEIGHTS )
			{
				final Blending blending = getBlending( img, blendingBorder, blendingRange, vd );

				weightImg = new TransformedRealRandomAccessibleInterval< FloatType >( blending, new FloatType(), transformedImg, transform, offset );
			}
			else //if ( processType == ProcessType.LOAD_WEIGHTS )
			{
				IOFunctions.println( "WARNING: LOADING WEIGHTS FROM: '" + new File( files[ i ] ) + "'" );
				ImagePlus imp = LegacyStackImgLoaderIJ.open( new File( files[ i ] ) );
				weightImg = imgFactory.create( bb.getDimensions(), new FloatType() );
				LegacyStackImgLoaderIJ.imagePlus2ImgLib2Img( imp, (Img< FloatType > )weightImg, false );
				imp.close();
				if ( debugImport )
				{
					imp = ImageJFunctions.show( weightImg );
					imp.setTitle( "ViewSetup " + vd.getViewSetupId() + " Timepoint " + vd.getTimePointId() );
				}
			}

			// split up into many parts for multithreading
			final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( Views.iterable( transformedImg ).size(), Threads.numThreads() * 4 );

			// set up executor service
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
			final ArrayList< Callable< String > > tasks = new ArrayList< Callable< String > >();

			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Transforming image & computing weights.");

			for ( final ImagePortion portion : portions )
			{
				if ( weightType == WeightType.WEIGHTS_ONLY )
				{
					final Interval imgInterval = new FinalInterval( ViewSetupUtils.getSizeOrLoad( vd.getViewSetup(), vd.getTimePoint(), spimData.getSequenceDescription().getImgLoader() ) );
					final Blending blending = getBlending( imgInterval, blendingBorder, blendingRange, vd );

					tasks.add( new TransformWeights( portion, imgInterval, blending, transform, overlapImg, weightImg, offset ) );
				}
				else if ( weightType == WeightType.PRECOMPUTED_WEIGHTS )
				{
					final Blending blending = getBlending( img, blendingBorder, blendingRange, vd );

					tasks.add( new TransformInputAndWeights( portion, img, blending, transform, transformedImg, weightImg, offset ) );
				}
				else if ( weightType == WeightType.NO_WEIGHTS || weightType == WeightType.VIRTUAL_WEIGHTS || weightType == WeightType.LOAD_WEIGHTS )
				{
					tasks.add( new TransformInput( portion, img, transform, transformedImg, offset ) );
				}
				else
				{
					throw new RuntimeException( weightType.name() + " not implemented yet." );
				}
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
				return false;
			}

			taskExecutor.shutdown();

			// extract PSFs if wanted
			if ( extractPSFs )
			{
				final ArrayList< double[] > llist = getLocationsOfCorrespondingBeads( timepoint, vd, extractPSFLabels.get( channel ).getLabel() );

				IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): Extracting PSF for viewsetup " + vd.getViewSetupId() +
					" using label '" + extractPSFLabels.get( channel ).getLabel() + "'" + " (" +llist.size() + " corresponding detections available)" );

				ePSF.extractNextImg( img, vd, transform, llist, psfSize );
			}
			
			if ( weightType != WeightType.WEIGHTS_ONLY )
				imgs.put( vd, transformedImg );
			weights.put( vd, weightImg );

			// remove temporarily loaded image
			tasks.clear();
			System.gc();
		}
		
		// normalize the weights
		final ArrayList< RandomAccessibleInterval< FloatType > > weightsSorted = new ArrayList< RandomAccessibleInterval< FloatType > >();

		for ( final ViewDescription vd : viewDescriptions )
		{
			weightsSorted.add( weights.get( vd ) );
			//new DisplayImage().exportImage(  weights.get( vd ), "w " + vd.getViewSetupId() );
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Computing weight normalization for deconvolution." );

		final WeightNormalizer wn;

		if ( weightType == WeightType.WEIGHTS_ONLY || weightType == WeightType.PRECOMPUTED_WEIGHTS || weightType == WeightType.LOAD_WEIGHTS )
			wn = new WeightNormalizer( weightsSorted );
		else if ( weightType == WeightType.VIRTUAL_WEIGHTS )
			wn = new WeightNormalizer( weightsSorted, imgFactory );
		else //if ( processType == ProcessType.NO_WEIGHTS )
			wn = null;

		if ( wn != null && !wn.process() )
			return false;

		// put the potentially modified weights back
		for ( int i = 0; i < viewDescriptions.size(); ++i )
		{
			weights.put( viewDescriptions.get( i ), weightsSorted.get( i ) );
			//new DisplayImage().exportImage( weightsSorted.get( i ), "w " + i );
		}

		if ( wn != null )
		{
			this.minOverlappingViews = wn.getMinOverlappingViews();
			this.avgOverlappingViews = wn.getAvgOverlappingViews();
	
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Minimal number of overlapping views: " + getMinOverlappingViews() + ", using " + ( this.minOverlappingViews = Math.max( 1, this.minOverlappingViews ) ) );
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Average number of overlapping views: " + getAvgOverlappingViews() + ", using " + ( this.avgOverlappingViews = Math.max( 1, this.avgOverlappingViews ) ) );
		}

		if ( osemIndex == 1 )
			osemspeedup = getMinOverlappingViews();
		else if ( osemIndex == 2 )
			osemspeedup = getAvgOverlappingViews();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Adjusting for OSEM speedup = " + osemspeedup );

		if ( weightType == WeightType.WEIGHTS_ONLY )
			displayWeights( osemspeedup, weightsSorted, overlapImg, imgFactory );
		else
			adjustForOSEM( weights, weightType, osemspeedup );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Finished precomputations for deconvolution." );

		//SimpleMultiThreading.threadHaltUnClean();

		return true;
	}

	private static void adjustForOSEM( final HashMap< ViewId, RandomAccessibleInterval< FloatType > > weights, final WeightType weightType, final double osemspeedup )
	{
		if ( osemspeedup == 1.0 )
			return;

		if ( weightType == WeightType.PRECOMPUTED_WEIGHTS || weightType == WeightType.WEIGHTS_ONLY || weightType == WeightType.LOAD_WEIGHTS )
		{
			for ( final RandomAccessibleInterval< FloatType > w : weights.values() )
			{
				for ( final FloatType f : Views.iterable( w ) )
					f.set( Math.min( 1, f.get() * (float)osemspeedup ) ); // individual contribution never higher than 1
			}
		}
		else if ( weightType == WeightType.NO_WEIGHTS )
		{
			for ( final RandomAccessibleInterval< FloatType > w : weights.values() )
			{
				final RandomAccess< FloatType > r = w.randomAccess();
				final long[] min = new long[ w.numDimensions() ];
				w.min( min );
				r.setPosition( min );
				r.get().set( Math.min( 1, r.get().get() * (float)osemspeedup ) ); // individual contribution never higher than 1
			}
		}
		else if ( weightType == WeightType.VIRTUAL_WEIGHTS )
		{
			for ( final RandomAccessibleInterval< FloatType > w : weights.values() )
				((NormalizingRandomAccessibleInterval< FloatType >) w).setOSEMspeedup( osemspeedup );
		}
		else
		{
			throw new RuntimeException( "Weight Type: " + weightType.name() + " not supported in ProcessForDeconvolution.adjustForOSEM()" );
		}
	}

	private ExtractPSF<FloatType> loadPSFs(
			final Channel ch,
			final ArrayList< ViewDescription > allInputData,
			final HashMap< Channel, ArrayList< Pair< Pair< Angle, Illumination >, String > > > psfFiles,
			final boolean transformLoadedPSFs )
	{
		final HashMap< ViewId, AffineTransform3D > models;
		
		if ( transformLoadedPSFs )
		{
			models = new HashMap< ViewId, AffineTransform3D >();
		
			for ( final ViewDescription viewDesc : allInputData )
				models.put( viewDesc, spimData.getViewRegistrations().getViewRegistration( viewDesc ).getModel() );
		}
		else
		{
			models = null;
		}

		return ExtractPSF.loadAndTransformPSFs( psfFiles.get( ch ), allInputData, new FloatType(), models );
	}

	protected ExtractPSF< FloatType > assignOtherChannel( final Channel channel, final HashMap< Channel, ChannelPSF > extractPSFLabels )
	{
		final ChannelPSF thisChannelPSF = extractPSFLabels.get( channel );
		final ChannelPSF otherChannelPSF = extractPSFLabels.get( thisChannelPSF.getOtherChannel() );

		final Channel otherChannel = thisChannelPSF.getOtherChannel();
		for ( int i = 0; i < viewDescriptions.size(); ++i )
		{
			// the viewid to map from
			final ViewDescription sourceVD = viewDescriptions.get( i );

			// search the viewid to map to
			for ( final ViewId viewId : viewIdsToProcess )
			{
				final ViewDescription otherVD = spimData.getSequenceDescription().getViewDescription( viewId );

				if (
					otherVD.getViewSetup().getAngle().getId() == sourceVD.getViewSetup().getAngle().getId() &&
					otherVD.getViewSetup().getIllumination().getId() == sourceVD.getViewSetup().getIllumination().getId() &&
					otherVD.getTimePointId() == sourceVD.getTimePointId() &&
					otherVD.getViewSetup().getChannel().getId() == otherChannel.getId() )
				{
					ePSF.getViewIdMapping().put( sourceVD, otherVD );

					IOFunctions.println(
							"ViewID=" + sourceVD.getViewSetupId() + ", TPID=" + sourceVD.getTimePointId() +
							" takes the PSF from " +
							"ViewID=" + otherVD.getViewSetupId() + ", TPID=" + otherVD.getTimePointId() );
				}
			}
		}

		return otherChannelPSF.getExtractPSFInstance();
	}

	protected ArrayList< double[] > getLocationsOfCorrespondingBeads( final TimePoint tp, final ViewDescription inputData, final String label )
	{
		final InterestPointList iplist = spimData.getViewInterestPoints().getViewInterestPointLists( inputData ).getInterestPointList( label );
		
		// we use a hashset as a detection can correspond with several other detections, and we only want it once
		final HashSet< Integer > ipWithCorrespondences = new HashSet< Integer >();
		
		for ( final CorrespondingInterestPoints cip : iplist.getCorrespondingInterestPoints() )
			ipWithCorrespondences.add( cip.getDetectionId() );
		
		final ArrayList< double[] > llist = new ArrayList< double[] >();
		
		// now go over all detections and see if they had correspondences
		for ( final InterestPoint ip : iplist.getInterestPoints() )
			if ( ipWithCorrespondences.contains( ip.getId() ) )
				llist.add( ip.getL().clone() );

		return llist;
	}
	
	protected void displayWeights(
			final double osemspeedup,
			final ArrayList< RandomAccessibleInterval< FloatType > > weights,
			final RandomAccessibleInterval< FloatType > overlapImg,
			final ImgFactory< FloatType > imgFactory )
	{
		final DisplayImage d = new DisplayImage();
		
		d.exportImage( overlapImg, bb, "Number of views per pixel" );
		
		final Img< FloatType > w = imgFactory.create( overlapImg, new FloatType() );
		final Img< FloatType > wosem = imgFactory.create( overlapImg, new FloatType() );
		
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( Views.iterable( weights.get( 0 ) ).size(), Threads.numThreads() * 2 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< String > > tasks = new ArrayList< Callable< String > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< String >() 
					{
						@Override
						public String call() throws Exception
						{
							final ArrayList< Cursor< FloatType > > cursors = new ArrayList< Cursor< FloatType > >(); 
							final Cursor< FloatType > sum = w.cursor();
							final Cursor< FloatType > sumOsem = wosem.cursor();
							
							for ( final RandomAccessibleInterval< FloatType > imgW : weights )
							{
								final Cursor< FloatType > c = Views.iterable( imgW ).cursor();
								c.jumpFwd( portion.getStartPosition() );
								cursors.add( c );
							}
							
							sum.jumpFwd( portion.getStartPosition() );
							sumOsem.jumpFwd( portion.getStartPosition() );
							
							for ( long j = 0; j < portion.getLoopSize(); ++j )
							{
								double sumW = 0;
								double sumOsemW = 0;
								
								for ( final Cursor< FloatType > c : cursors )
								{
									final float w = c.next().get();
									sumW += w;
									sumOsemW += Math.min( 1, w * osemspeedup );
								}

								sum.next().set( (float)sumW );
								sumOsem.next().set( (float)sumOsemW );
							}
							
							return "done.";
						}
					});
		}
		
		// run threads
		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );			
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to compute weight normalization for deconvolution: " + e );
			e.printStackTrace();
			return;
		}

		taskExecutor.shutdown();

		d.exportImage( w, bb, "Sum of weights per pixel" );
		d.exportImage( wosem, bb, "OSEM=" + osemspeedup + ", sum of weights per pixel" );
	}

	protected Blending getBlending( final Interval interval, final int[] blendingBorder, final int[] blendingRange, final ViewDescription desc )
	{
		final float[] blending = new float[ 3 ];
		final float[] border = new float[ 3 ];
		
		blending[ 0 ] = blendingRange[ 0 ];
		blending[ 1 ] = blendingRange[ 1 ];
		blending[ 2 ] = blendingRange[ 2 ];

		border[ 0 ] = blendingBorder[ 0 ];
		border[ 1 ] = blendingBorder[ 1 ];
		border[ 2 ] = blendingBorder[ 2 ];

		return new Blending( interval, border, blending );
	}
}
