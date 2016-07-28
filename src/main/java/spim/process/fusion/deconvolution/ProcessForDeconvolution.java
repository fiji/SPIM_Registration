package spim.process.fusion.deconvolution;

import static mpicbg.spim.data.generic.sequence.ImgLoaderHints.LOAD_COMPLETELY;
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
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
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
import spim.process.fusion.deconvolution.normalize.NormalizingPartyVirtualRandomAccessibleInterval;
import spim.process.fusion.deconvolution.normalize.WeightNormalizer;
import spim.process.fusion.deconvolution.normalize.WeightNormalizerConstant;
import spim.process.fusion.deconvolution.normalize.WeightNormalizerPrecomputed;
import spim.process.fusion.deconvolution.normalize.WeightNormalizerVirtual;
import spim.process.fusion.deconvolution.normalize.WeightNormalizerPartlyVirtual;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.transformed.TransformedInputRandomAccessible;
import spim.process.fusion.transformed.TransformedRasteredRealRandomAccessible;
import spim.process.fusion.transformed.weights.BlendingRealRandomAccessible;
import bdv.util.ConstantRandomAccessible;

/**
 * Fused individual images for each input stack, uses the exporter directly
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ProcessForDeconvolution
{
	public static enum WeightType { NO_WEIGHTS, PARTLY_VIRTUAL_WEIGHTS, FULLY_VIRTUAL_WEIGHTS, PRECOMPUTED_WEIGHTS, LOAD_WEIGHTS };
	public static enum ImgType { NO_IMGS, VIRTUAL_IMGS, PRECOMPUTED_IMGS };

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
			final ImgType imgType,
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

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Transforming view " + (i + 1) + " of " + (viewDescriptions.size()) + " (viewsetup=" + vd.getViewSetupId() + ", tp=" + vd.getTimePointId() + ")" );
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Reserving memory for transformed & weight image.");

			// define the output
			final Interval outputInterval = new FinalInterval( bb.getDimensions() );
			final RandomAccessibleInterval< FloatType > tImg, tWeight;

			// set up transformations
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Setup transformations.");

			spimData.getViewRegistrations().getViewRegistration( vd ).updateModel();
			final AffineTransform3D transform = spimData.getViewRegistrations().getViewRegistration( vd ).getModel();
			final long[] offset = new long[]{ bb.min( 0 ), bb.min( 1 ), bb.min( 2 ) };

			// set up the image
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Loading input image ..." );

			// loading the input if necessary
			final Interval inputImgInterval;
			final RandomAccessibleInterval< ? > inputImg;

			if ( imgType == ImgType.NO_IMGS )
			{
				IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Loading image dimensions only (no input).");
				inputImgInterval = new FinalInterval( ViewSetupUtils.getSizeOrLoad( vd.getViewSetup(), vd.getTimePoint(), spimData.getSequenceDescription().getImgLoader() ) );
				inputImg = Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 0 ), bb.numDimensions() ), inputImgInterval );
			}
			else
			{
				IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Loading image using " + spimData.getSequenceDescription().getImgLoader().getClass().getSimpleName() );

				final Object type = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( vd.getViewSetupId() ).getImageType();
				
				if ( !RealType.class.isInstance( type ) )
					throw new RuntimeException( "Cannot load image, type == " + type.getClass().getSimpleName() );
				else
					IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Image Type = " + type.getClass().getSimpleName() );

				inputImg = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( vd.getViewSetupId() ).getImage( vd.getTimePointId() );

				IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Input image class: " + inputImg.getClass().getSimpleName() );
				
				if ( Img.class.isInstance( inputImg ) )
				{
					if ( ((Img< ? >)inputImg).factory() == null )
						IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Input image factory: NULL" );
					else
						IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Input image factory: " + ((Img< ? >)inputImg).factory().getClass().getSimpleName() );
				}

				inputImgInterval = new FinalInterval( inputImg );
			}

			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Image dimensions: " + inputImgInterval.dimension( 0 ) + "x" + inputImgInterval.dimension( 1 ) + "x" + inputImgInterval.dimension( 2 ) + " px." );

			//
			// initializing transformed image
			//
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Initializing transformed image: " + imgType.name() );

			if ( imgType == ImgType.VIRTUAL_IMGS || imgType == ImgType.PRECOMPUTED_IMGS )
			{
				// the virtual transformed image construct
				final RandomAccessible< FloatType > virtual = new TransformedInputRandomAccessible( inputImg, transform, true, MVDeconvolution.minValue, new FloatType( 0 ), offset );
				final RandomAccessibleInterval< FloatType > virtualInterval = Views.interval( virtual, outputInterval );

				if ( imgType == ImgType.PRECOMPUTED_IMGS )
				{
					IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Transformed image factory: " + imgFactory.getClass().getSimpleName() );
					tImg = imgFactory.create( bb.getDimensions(), new FloatType() );

					// copy the virtual construct into an actual image
					long t = System.currentTimeMillis();
					FusionHelper.copyImg( virtualInterval, tImg );
					System.out.println( "copy img: " +  ( System.currentTimeMillis() - t ) );
				}
				else
				{
					tImg = virtualInterval;
				}
			}
			else //if ( imgType == ImgType.NO_IMGS )
			{
				tImg = Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 0 ), bb.numDimensions() ), outputInterval );
			}

			//
			// initializing weights
			//

			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Initializing weights: " + weightType.name() );

			if ( weightType == WeightType.PARTLY_VIRTUAL_WEIGHTS || weightType == WeightType.FULLY_VIRTUAL_WEIGHTS || weightType == WeightType.PRECOMPUTED_WEIGHTS )
			{
				// the virtual weight construct
				final RandomAccessible< FloatType > virtual = 
						new TransformedRasteredRealRandomAccessible< FloatType >(
							new BlendingRealRandomAccessible( inputImgInterval, blendingBorder, blendingRange ),
							new FloatType(),
							transform,
							offset );
				final RandomAccessibleInterval< FloatType > virtualInterval = Views.interval( virtual, outputInterval );

				if ( weightType == WeightType.PRECOMPUTED_WEIGHTS )
				{
					IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Weight image factory: " + imgFactory.getClass().getSimpleName() );
					tWeight = imgFactory.create( bb.getDimensions(), new FloatType() );

					// copy the virtual construct into an actual image
					long t = System.currentTimeMillis();
					FusionHelper.copyImg( virtualInterval, tWeight );
					System.out.println( "copy weight: " +  ( System.currentTimeMillis() - t ) );
				}
				else
				{
					tWeight = virtualInterval;
				}
			}
			else if ( weightType == WeightType.NO_WEIGHTS )
			{
				tWeight = Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), bb.numDimensions() ), outputInterval );
			}
			else //if ( weightType == WeightType.LOAD_WEIGHTS )
			{
				IOFunctions.println( "WARNING: LOADING WEIGHTS FROM: '" + new File( files[ i ] ) + "'" );
				ImagePlus imp = LegacyStackImgLoaderIJ.open( new File( files[ i ] ) );
				tWeight = imgFactory.create( bb.getDimensions(), new FloatType() );
				LegacyStackImgLoaderIJ.imagePlus2ImgLib2Img( imp, (Img< FloatType > )tWeight, false );
				imp.close();
				if ( debugImport )
				{
					imp = ImageJFunctions.show( tWeight );
					imp.setTitle( "ViewSetup " + vd.getViewSetupId() + " Timepoint " + vd.getTimePointId() );
				}
			}

			// extract PSFs if wanted
			if ( extractPSFs )
			{
				final ArrayList< double[] > llist = getLocationsOfCorrespondingBeads( timepoint, vd, extractPSFLabels.get( channel ).getLabel() );

				IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): Extracting PSF for viewsetup " + vd.getViewSetupId() +
					" using label '" + extractPSFLabels.get( channel ).getLabel() + "'" + " (" +llist.size() + " corresponding detections available)" );

				ePSF.extractNextImg( floatInterval( inputImg ), vd, transform, llist, psfSize );
			}

			imgs.put( vd, tImg );
			weights.put( vd, tWeight );

			//ImageJFunctions.show( tImg );
			//ImageJFunctions.show( tWeight );

			// remove temporarily loaded image
			System.gc();
		}

		//SimpleMultiThreading.threadHaltUnClean();

		// normalize the weights
		final ArrayList< RandomAccessibleInterval< FloatType > > weightsSorted = new ArrayList< RandomAccessibleInterval< FloatType > >();

		for ( final ViewDescription vd : viewDescriptions )
		{
			weightsSorted.add( weights.get( vd ) );
			//new DisplayImage().exportImage(  weights.get( vd ), "w " + vd.getViewSetupId() );
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Computing weight normalization for deconvolution." );

		// normalizes the weights (sum == 1) and applies osem-speedup if wanted
		// replaces the instances in weightsSorted if necessary (e.g. for virtual weights)
		final WeightNormalizer wn;

		if ( weightType == WeightType.PRECOMPUTED_WEIGHTS || weightType == WeightType.LOAD_WEIGHTS )
			wn = new WeightNormalizerPrecomputed( weightsSorted );
		else if ( weightType == WeightType.PARTLY_VIRTUAL_WEIGHTS )
			wn = new WeightNormalizerPartlyVirtual( weightsSorted, imgFactory );
		else if ( weightType == WeightType.FULLY_VIRTUAL_WEIGHTS )
			wn = new WeightNormalizerVirtual( weightsSorted );
		else if ( weightType == WeightType.NO_WEIGHTS )
			wn = new WeightNormalizerConstant( weightsSorted );
		else
			throw new RuntimeException( "Unknown weight type: " + weightType );

		if ( !wn.process() )
			return false;

		// put the potentially modified weights back
		for ( int i = 0; i < viewDescriptions.size(); ++i )
		{
			weights.put( viewDescriptions.get( i ), weightsSorted.get( i ) );
			//new DisplayImage().exportImage( weightsSorted.get( i ), "w " + i );
		}

		this.minOverlappingViews = wn.getMinOverlappingViews();
		this.avgOverlappingViews = wn.getAvgOverlappingViews();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Minimal number of overlapping views: " + getMinOverlappingViews() + ", using " + ( this.minOverlappingViews = Math.max( 1, this.minOverlappingViews ) ) );
		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Average number of overlapping views: " + getAvgOverlappingViews() + ", using " + ( this.avgOverlappingViews = Math.max( 1, this.avgOverlappingViews ) ) );

		if ( osemIndex == 1 )
			osemspeedup = getMinOverlappingViews();
		else if ( osemIndex == 2 )
			osemspeedup = getAvgOverlappingViews();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Adjusting for OSEM speedup = " + osemspeedup );

		wn.adjustForOSEM( osemspeedup );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Finished precomputations for deconvolution." );

		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static < T extends RealType< T > > RandomAccessibleInterval< FloatType > floatInterval( final RandomAccessibleInterval interval )
	{
		if ( FloatType.class.isInstance( Views.iterable( interval ).firstElement() ) )
		{
			return (RandomAccessibleInterval< FloatType >)interval;
		}
		else
		{
			return new ConvertedRandomAccessibleInterval< T, FloatType >(
					interval,
					new RealFloatConverter< T >(),
					new FloatType() );
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
}
