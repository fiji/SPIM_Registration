package spim.process.fusion.deconvolution;

import java.awt.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import spim.Threads;
import spim.fiji.ImgLib2Temp;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.weightedavg.ProcessFusion;
import spim.process.fusion.weights.Blending;

/**
 * Fused individual images for each input stack, uses the exporter directly
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ProcessForDeconvolution
{
	public static int defaultBlendingRangeNumber = 25;
	public static int defaultBlendingBorderNumber = 15;
	public static int[] defaultBlendingRange = new int[]{ defaultBlendingRangeNumber, defaultBlendingRangeNumber, defaultBlendingRangeNumber };
	public static int[] defaultBlendingBorder = null;

	final protected SpimData2 spimData;
	final protected List< ViewId > viewIdsToProcess;
	final BoundingBoxGUI bb;
	final int[] blendingBorder;
	final int[] blendingRange;
	
	int minOverlappingViews;
	double avgOverlappingViews;
	ArrayList< ViewDescription > viewDescriptions;
	HashMap< ViewId, Img< FloatType > > imgs, weights;
	ExtractPSF< FloatType > ePSF;
	
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
	public HashMap< ViewId, Img< FloatType > > getTransformedImgs() { return imgs; }
	public HashMap< ViewId, Img< FloatType > > getTransformedWeights() { return weights; }
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
			final int osemIndex,
			double osemspeedup,
			final boolean weightsOnly,
			final HashMap< Channel, ChannelPSF > extractPSFLabels,
			final long[] psfSize,
			final HashMap< Channel, ArrayList< Pair< Pair< Angle, Illumination >, String > > > psfFiles,
			final boolean transformLoadedPSFs )
	{
		// get all views that are fused
		this.viewDescriptions = FusionHelper.assembleInputData( spimData, timepoint, channel, viewIdsToProcess );

		if ( this.viewDescriptions.size() == 0 )
			return false;

		this.imgs = new HashMap< ViewId, Img< FloatType > >();
		this.weights = new HashMap< ViewId, Img< FloatType > >();
		
		final Img< FloatType > overlapImg;
		
		if ( weightsOnly )
			overlapImg = bb.getImgFactory( new FloatType() ).create( bb.getDimensions(), new FloatType() );
		else
			overlapImg = null;
				
		final boolean extractPSFs = (extractPSFLabels != null) && (extractPSFLabels.get( channel ).getLabel() != null);
		final boolean loadPSFs = (psfFiles != null);
				
		if ( extractPSFs )
			ePSF = new ExtractPSF<FloatType>( bb.getImgFactory( new FloatType() ) );
		else if ( loadPSFs )
			ePSF = loadPSFs( channel, viewDescriptions, psfFiles, transformLoadedPSFs );
		else
			ePSF = assignOtherChannel( channel, extractPSFLabels );

		if ( ePSF == null )
			return false;

		// remember the extracted or loaded PSFs
		extractPSFLabels.get( channel ).setExtractPSFInstance( ePSF );

		// we will need to run some batches until all is fused
		for ( int i = 0; i < viewDescriptions.size(); ++i )
		{
			IOFunctions.println( "Fusing view " + i + " of " + (viewDescriptions.size()-1) );
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused & weight image.");

			// try creating the output (type needs to be there to define T)
			final Img< FloatType > fusedImg; 
			
			if ( weightsOnly )
				fusedImg = overlapImg;
			else
				fusedImg = bb.getImgFactory( new FloatType() ).create( bb.getDimensions(), new FloatType() );
			
			final Img< FloatType > weightImg = fusedImg.factory().create( bb.getDimensions(), new FloatType() );

			if ( fusedImg == null || weightImg == null )
			{
				IOFunctions.println( "ProcessForDeconvolution: Cannot create output images."  );
				return false;
			}
	
			final ViewDescription inputData = viewDescriptions.get( i );
			
			// same as in the paralell fusion now more or less
			final RandomAccessibleInterval< FloatType > img;
			
			if ( weightsOnly && !extractPSFs )
				img = null;
			else
				img = ProcessFusion.getImage( new FloatType(), spimData, inputData, true );
						
			// split up into many parts for multithreading
			final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( fusedImg.size(), Threads.numThreads() * 4 );

			// set up executor service
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
			final ArrayList< Callable< String > > tasks = new ArrayList< Callable< String > >();

			for ( final ImagePortion portion : portions )
				if ( weightsOnly )
				{
					final Interval imgInterval = ImgLib2Temp.getIntervalFromDimension( ViewSetupUtils.getSizeOrLoad( inputData.getViewSetup(), inputData.getTimePoint(), spimData.getSequenceDescription().getImgLoader() ) );
					// TODO: change back to imglib2 implementation once uploaded to Fiji
					//final Interval imgInterval = new FinalInterval( ViewSetupUtils.getSizeOrLoad( inputData.getViewSetup(), inputData.getTimePoint(), spimData.getSequenceDescription().getImgLoader() ) );
					
					tasks.add( new ProcessForOverlapOnlyPortion(
							portion,
							imgInterval,
							getBlending( imgInterval, blendingBorder, blendingRange, inputData ),
							spimData.getViewRegistrations().getViewRegistration( inputData ).getModel(),
							overlapImg,
							weightImg,
							bb ) );					
				}
				else
				{
					tasks.add( new ProcessForDeconvolutionPortion(
							portion,
							img,
							getBlending( img, blendingBorder, blendingRange, inputData ),
							spimData.getViewRegistrations().getViewRegistration( inputData ).getModel(),
							fusedImg,
							weightImg,
							bb ) );
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
				final ArrayList< double[] > llist = getLocationsOfCorrespondingBeads( timepoint, inputData, extractPSFLabels.get( channel ).getLabel() );
				
				IOFunctions.println( "Extracting PSF for viewsetup " + inputData.getViewSetupId() + " using label '" + extractPSFLabels.get( channel ).getLabel() + "'" +
						" (" +llist.size() + " corresponding detections available)" );
				
				ePSF.extractNextImg(
						img,
						inputData,
						spimData.getViewRegistrations().getViewRegistration( inputData ).getModel(),
						llist,
						psfSize );
			}
			
			if ( !weightsOnly )
				imgs.put( inputData, fusedImg );
			weights.put( inputData, weightImg );
		}
		
		// normalize the weights
		final ArrayList< Img< FloatType > > weightsSorted = new ArrayList< Img< FloatType> >();

		for ( final ViewDescription vd : viewDescriptions )
			weightsSorted.add( weights.get( vd ) );

		if ( !normalizeWeightsAndComputeMinAvgViews( weightsSorted ) )
			return false;
				
		IOFunctions.println( "Minimal number of overlapping views: " + getMinOverlappingViews() + ", using " + (this.minOverlappingViews = Math.max( 1, this.minOverlappingViews ) ) );
		IOFunctions.println( "Average number of overlapping views: " + getAvgOverlappingViews() + ", using " + (this.avgOverlappingViews = Math.max( 1, this.avgOverlappingViews ) ) );

		if ( weightsOnly )
		{
			if ( osemIndex == 1 )
				osemspeedup = getMinOverlappingViews();
			else if ( osemIndex == 2 )
				osemspeedup = getAvgOverlappingViews();

			if( !GraphicsEnvironment.isHeadless() )
				displayWeights( osemspeedup, weightsSorted, overlapImg );
		}
				
		return true;
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

		return ExtractPSF.loadAndTransformPSFs( psfFiles.get( ch ), allInputData, bb.getImgFactory( new FloatType() ), new FloatType(), models );
	}

	protected ExtractPSF< FloatType > assignOtherChannel( final Channel channel, final HashMap< Channel, ChannelPSF > extractPSFLabels )
	{
		final ChannelPSF thisChannelPSF = extractPSFLabels.get( channel );
		final ChannelPSF otherChannelPSF = extractPSFLabels.get( thisChannelPSF.getOtherChannel() );
		
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
	
	protected void displayWeights( final double osemspeedup, final ArrayList< Img< FloatType > > weights, final Img< FloatType > overlapImg )
	{
		final DisplayImage d = new DisplayImage();
		
		d.exportImage( overlapImg, bb, "Number of views per pixel" );
		
		final Img< FloatType > w = overlapImg.factory().create( overlapImg, new FloatType() );
		final Img< FloatType > wosem = overlapImg.factory().create( overlapImg, new FloatType() );
		
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( weights.get( 0 ).size(), Threads.numThreads() * 2 );

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
							
							for ( final Img< FloatType > imgW : weights )
							{
								final Cursor< FloatType > c = imgW.cursor();
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
	
	protected boolean normalizeWeightsAndComputeMinAvgViews( final List< Img< FloatType > > weights )
	{
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( weights.get( 0 ).size(), Threads.numThreads() * 2 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< double[] > > tasks = new ArrayList< Callable< double[] > >();
		
		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< double[] >() 
					{
						@Override
						public double[] call() throws Exception
						{
							final ArrayList< Cursor< FloatType > > cursors = new ArrayList< Cursor< FloatType > >(); 
							
							for ( final Img< FloatType > imgW : weights )
							{
								final Cursor< FloatType > c = imgW.cursor();
								c.jumpFwd( portion.getStartPosition() );
								cursors.add( c );
							}
						
							int minNumViews = cursors.size();
							long countViews = 0;
							
							for ( long j = 0; j < portion.getLoopSize(); ++j )
							{
								double sumW = 0;
								int count = 0;
								
								for ( final Cursor< FloatType > c : cursors )
								{
									final float w = c.next().get(); 
									sumW += w;
									
									if ( w > 0 )
										++count;
								}
								
								countViews += count;
								minNumViews = Math.min( minNumViews, count );
								
								if ( sumW > 1 )
									for ( final Cursor< FloatType > c : cursors )
										c.get().set( (float)( c.get().get() / sumW ) );
							}
							
							final double avgNumViews = (double)countViews / (double)( portion.getLoopSize() );
							
							return new double[]{ minNumViews, avgNumViews };
						}
					});
		}
		
		// run threads
		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< double[] > > futures = taskExecutor.invokeAll( tasks );
			
			this.minOverlappingViews = weights.size();
			this.avgOverlappingViews = 0;
			
			for ( final Future< double[] > f : futures )
			{
				final double[] minAvg = f.get();
				
				this.minOverlappingViews = Math.min( this.minOverlappingViews, (int)Math.round( minAvg[ 0 ] ) );
				this.avgOverlappingViews += minAvg[ 1 ];
			}
			
			this.avgOverlappingViews /= futures.size();
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to compute weight normalization for deconvolution: " + e );
			e.printStackTrace();
			return false;
		}

		taskExecutor.shutdown();

		return true;
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
