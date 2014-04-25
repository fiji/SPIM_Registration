package spim.process.fusion.deconvolution;

import java.util.ArrayList;
import java.util.Date;
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
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.export.DisplayImage;
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
	final protected ArrayList<Angle> anglesToProcess;
	final protected ArrayList<Illumination> illumsToProcess;
	final BoundingBox bb;
	final int[] blendingBorder;
	final int[] blendingRange;
	
	int minOverlappingViews;
	double avgOverlappingViews;

	public ProcessForDeconvolution(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final BoundingBox bb,
			final int[] blendingBorder,
			final int[] blendingRange )
	{
		this.spimData = spimData;
		this.anglesToProcess = anglesToProcess;
		this.illumsToProcess = illumsToProcess;
		this.bb = bb;
		this.blendingBorder = blendingBorder;
		this.blendingRange = blendingRange;
	}
	
	public int getMinOverlappingViews() { return minOverlappingViews; }
	public double getAvgOverlappingViews() { return avgOverlappingViews; }

	/** 
	 * Fuses one stack, i.e. all angles/illuminations for one timepoint and channel
	 * 
	 * @param type
	 * @param imgFactoryType
	 * @param timepoint
	 * @param channel
	 * @return
	 */
	public boolean fuseStacks(
			final TimePoint timepoint, 
			final Channel channel,
			final int osemIndex,
			double osemspeedup,
			final boolean weightsOnly )
	{				
		// get all views that are fused
		final ArrayList< ViewDescription< TimePoint, ViewSetup > > allInputData =
				FusionHelper.assembleInputData( spimData, timepoint, channel, anglesToProcess, illumsToProcess );
		
		final ArrayList< Img< FloatType > > imgs = new ArrayList< Img< FloatType > >(); 
		final ArrayList< Img< FloatType > > weights = new ArrayList< Img< FloatType > >();
		
		final Img< FloatType > overlapImg;
		
		if ( weightsOnly )
			overlapImg = bb.getImgFactory( new FloatType() ).create( bb.getDimensions(), new FloatType() );
		else
			overlapImg = null;
		
		// we will need to run some batches until all is fused
		for ( int i = 0; i < allInputData.size(); ++i )
		{
			IOFunctions.println( "Fusing view " + i + " of " + (allInputData.size()-1) );
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
	
			final ViewDescription< TimePoint, ViewSetup > inputData = allInputData.get( i );
			
			// same as in the paralell fusion now more or less
			final RandomAccessibleInterval< FloatType > img;
			
			if ( weightsOnly )
				img = null;
			else
				img = getImage( new FloatType(), spimData, inputData, true );
						
			// split up into many parts for multithreading
			final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( fusedImg.size(), Runtime.getRuntime().availableProcessors() * 4 );

			// set up executor service
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
			final ArrayList< Callable< String > > tasks = new ArrayList< Callable< String > >();

			for ( final ImagePortion portion : portions )
				if ( weightsOnly )
				{
					final Interval imgInterval = new FinalInterval(
							new long[]{ 0, 0, 0 },
							new long[]{ 
									inputData.getViewSetup().getWidth() - 1,
									inputData.getViewSetup().getHeight() - 1,
									inputData.getViewSetup().getDepth() - 1 } );
					
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
			
			if ( !weightsOnly )
				imgs.add( fusedImg );
			weights.add( weightImg );
		}
		
		// normalize the weights
		if ( !normalizeWeightsAndComputeMinAvgViews( weights ) )
			return false;
		
		IOFunctions.println( "Minimal number of overlapping views: " + getMinOverlappingViews() + ", using " + (this.minOverlappingViews = Math.max( 1, this.minOverlappingViews ) ) );
		IOFunctions.println( "Average number of overlapping views: " + getAvgOverlappingViews() + ", using " + (this.avgOverlappingViews = Math.max( 1, this.avgOverlappingViews ) ) );

		if ( weightsOnly )
		{
			if ( osemIndex == 1 )
				osemspeedup = getMinOverlappingViews();
			else if ( osemIndex == 2 )
				osemspeedup = getAvgOverlappingViews();
				
			displayWeights( osemspeedup, weights, overlapImg );
		}
				
		return true;
	}
	
	protected void displayWeights( final double osemspeedup, final ArrayList< Img< FloatType > > weights, final Img< FloatType > overlapImg )
	{
		final DisplayImage d = new DisplayImage();
		
		d.exportImage( overlapImg, bb, "Number of views per pixel" );
		
		final Img< FloatType > w = overlapImg.factory().create( overlapImg, new FloatType() );
		final Img< FloatType > wosem = overlapImg.factory().create( overlapImg, new FloatType() );
		
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( weights.get( 0 ).size(), Runtime.getRuntime().availableProcessors() * 2 );

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
	
	protected boolean normalizeWeightsAndComputeMinAvgViews( final ArrayList< Img< FloatType > > weights )
	{
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( weights.get( 0 ).size(), Runtime.getRuntime().availableProcessors() * 2 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
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
							
							final double avgNumViews = (double)countViews / (double)( portion.getLoopSize() * weights.size() );
							
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
			
			this.avgOverlappingViews /= (double)weights.size();
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

	protected Blending getBlending( final Interval interval, final int[] blendingBorder, final int[] blendingRange, final ViewDescription< TimePoint, ViewSetup > desc )
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

	@SuppressWarnings("unchecked")
	protected static < T extends RealType< T > > RandomAccessibleInterval< T > getImage( final T type, final SpimData2 spimData, final ViewDescription<TimePoint, ViewSetup> view, final boolean normalize )
	{
		if ( type instanceof FloatType )
			return (RandomAccessibleInterval< T >)(Object)spimData.getSequenceDescription().getImgLoader().getImage( view, normalize );
		else if ( type instanceof UnsignedShortType )
			return (RandomAccessibleInterval< T >)(Object)spimData.getSequenceDescription().getImgLoader().getUnsignedShortImage( view );
		else
			return null;
	}
}
