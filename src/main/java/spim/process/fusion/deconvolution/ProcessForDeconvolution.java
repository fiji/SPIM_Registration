package spim.process.fusion.deconvolution;

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
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
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
	public static int defaultBlendingRangeNumber = 40;
	public static int defaultBlendingBorderNumber = 15;
	public static int[] defaultBlendingRange = new int[]{ defaultBlendingRangeNumber, defaultBlendingRangeNumber, defaultBlendingRangeNumber };
	public static int[] defaultBlendingBorder = null;

	final protected SpimData2 spimData;
	final protected ArrayList<Angle> anglesToProcess;
	final protected ArrayList<Illumination> illumsToProcess;
	final BoundingBox bb;
	final int blendingBorder;
	final int blendingRange;

	public ProcessForDeconvolution(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final BoundingBox bb,
			final int blendingBorder,
			final int blendingRange )
	{
		this.spimData = spimData;
		this.anglesToProcess = anglesToProcess;
		this.illumsToProcess = illumsToProcess;
		this.bb = bb;
		this.blendingBorder = blendingBorder;
		this.blendingRange = blendingRange;
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
	public boolean fuseStacks(
			final TimePoint timepoint, 
			final Channel channel )
	{				
		// get all views that are fused
		final ArrayList< ViewDescription< TimePoint, ViewSetup > > allInputData =
				FusionHelper.assembleInputData( spimData, timepoint, channel, anglesToProcess, illumsToProcess );
		
		// we will need to run some batches until all is fused
		for ( int i = 0; i < allInputData.size(); ++i )
		{
			IOFunctions.println( "Fusing view " + i + " of " + (allInputData.size()-1) );
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused & weight image.");

			// try creating the output (type needs to be there to define T)
			final Img< FloatType > fusedImg = bb.getImgFactory( new FloatType() ).create( bb.getDimensions(), new FloatType() );
			final Img< FloatType > weightImg = fusedImg.factory().create( bb.getDimensions(), new FloatType() );

			if ( fusedImg == null || weightImg == null )
			{
				IOFunctions.println( "ProcessForDeconvolution: Cannot create output images."  );
				return false;
			}
	
			final ViewDescription< TimePoint, ViewSetup > inputData = allInputData.get( i );
			
			// same as in the paralell fusion now more or less
			final RandomAccessibleInterval< FloatType > img = getImage( new FloatType(), spimData, inputData );
						
			// split up into many parts for multithreading
			final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( fusedImg.size(), Runtime.getRuntime().availableProcessors() * 4 );

			// set up executor service
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
			final ArrayList< ProcessForDeconvolutionPortion > tasks = new ArrayList< ProcessForDeconvolutionPortion >();

			for ( final ImagePortion portion : portions )
				tasks.add( new ProcessForDeconvolutionPortion(
						portion,
						img,
						getBlending( img, blendingBorder, blendingRange, inputData ),
						spimData.getViewRegistrations().getViewRegistration( inputData ).getModel(),
						fusedImg,
						weightImg,
						bb ) );
			
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
		}
		
		return true;
	}

	protected Blending getBlending( final Interval interval, final int blendingBorder, final int blendingRange, final ViewDescription< TimePoint, ViewSetup > desc )
	{
		final float[] blending = new float[ 3 ];
		final float[] border = new float[ 3 ];
		
		final float minRes = (float)ProcessFusion.getMinRes( desc.getViewSetup() );
		
		blending[ 0 ] = blendingRange / ((float)desc.getViewSetup().getPixelWidth() / minRes);
		blending[ 1 ] = blendingRange / ((float)desc.getViewSetup().getPixelHeight() / minRes);
		blending[ 2 ] = blendingRange / ((float)desc.getViewSetup().getPixelDepth() / minRes);

		border[ 0 ] = blendingBorder / ((float)desc.getViewSetup().getPixelWidth() / minRes);
		border[ 1 ] = blendingBorder / ((float)desc.getViewSetup().getPixelHeight() / minRes);
		border[ 2 ] = blendingBorder / ((float)desc.getViewSetup().getPixelDepth() / minRes);
		
		return new Blending( interval, border, blending );
	}

	@SuppressWarnings("unchecked")
	protected static < T extends RealType< T > > RandomAccessibleInterval< T > getImage( final T type, final SpimData2 spimData, final ViewDescription<TimePoint, ViewSetup> view )
	{
		if ( type instanceof FloatType )
			return (RandomAccessibleInterval< T >)(Object)spimData.getSequenceDescription().getImgLoader().getImage( view, false );
		else if ( type instanceof UnsignedShortType )
			return (RandomAccessibleInterval< T >)(Object)spimData.getSequenceDescription().getImgLoader().getUnsignedShortImage( view );
		else
			return null;
	}
}
