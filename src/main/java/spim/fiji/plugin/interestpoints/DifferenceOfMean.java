package spim.fiji.plugin.interestpoints;

import ij.ImagePlus;
import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.wrapper.ImgLib2;
import mpicbg.models.Point;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.segmentation.InteractiveIntegral;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import spim.fiji.spimdata.SpimDataBeads;
import spim.process.interestpoints.ProcessDOM;


public class DifferenceOfMean extends DifferenceOf
{
	public static int defaultR1 = 2;
	public static int defaultR2 = 3;
	public static double defaultT = 0.02;
	
	public static int defaultRadius1[];
	public static int defaultRadius2[];
	public static double defaultThreshold[];
	public static boolean defaultFindMin[];
	public static boolean defaultFindMax[];
	
	int[] radius1;
	int[] radius2;
	double[] threshold;
	boolean[] findMin;
	boolean[] findMax;
	
	@Override
	public String getDescription() { return "Difference-of-Mean (Integral image based)"; }

	@Override
	public DifferenceOfMean newInstance() { return new DifferenceOfMean(); }

	@Override
	public HashMap< ViewId, List<Point> > findInterestPoints( final SpimDataBeads spimData, final ArrayList< Channel> channelsToProcess, final ArrayList< TimePoint > timepointsToProcess )
	{
		final HashMap< ViewId, List< Point > > interestPoints = new HashMap< ViewId, List< Point > >();
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Angle a : spimData.getSequenceDescription().getAllAngles() )
				for ( final Illumination i : spimData.getSequenceDescription().getAllIlluminations() )
					for ( final Channel c : channelsToProcess )
					{
						//
						// open the corresponding image (if present at this timepoint)
						//
						long time1 = System.currentTimeMillis();
						final ViewId viewId = DifferenceOf.getViewId( spimData.getSequenceDescription(), t, c, a, i );

						if ( viewId == null )
							IOFunctions.println( "An error occured. Count not find the corresponding ViewSetup for angle: " + 
								a.getId() + " channel: " + c.getId() + " illum: " + i.getId() );

						final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );

						if ( !viewDescription.isPresent() )
							continue;
						
						final Image< FloatType > img = ImgLib2.wrapFloatToImgLib1( 
							(Img<net.imglib2.type.numeric.real.FloatType>)
								spimData.getSequenceDescription().getImgLoader().getImage( viewDescription, false ) );
						
						long time2 = System.currentTimeMillis();
						
						benchmark.openFiles += time2 - time1;
						
						//
						// compute Difference-of-Mean
						//
						interestPoints.put( viewId, ProcessDOM.compute( img, radius1[ c.getId() ], radius2[ c.getId() ], (float)threshold[ c.getId() ], localization, findMin[ c.getId() ], findMax[ c.getId() ] ) );
						img.close();

				        benchmark.computation += System.currentTimeMillis() - time2;
					}

		return interestPoints;
	}
	
	@Override
	protected boolean setDefaultValues( final Channel channel, final int brightness )
	{
		final int channelId = channel.getId();
		
		this.radius1[ channelId ] = defaultR1;
		this.radius2[ channelId ] = defaultR2;
		this.findMin[ channelId ] = false;
		this.findMax[ channelId ] = true;
		
		if ( brightness == 0 )
			this.threshold[ channelId ] = 0.0025f;
		else if ( brightness == 1 )
			this.threshold[ channelId ] = 0.02f;
		else if ( brightness == 2 )
			this.threshold[ channelId ] = 0.075f;
		else if ( brightness == 3 )
			this.threshold[ channelId ] = 0.25f;
		else
			return false;
		
		return true;
	}

	@Override
	protected boolean setAdvancedValues( final Channel channel )
	{
		final int channelId = channel.getId();
		
		final GenericDialog gd = new GenericDialog( "Advanced values for channel " + channel.getName() );
		
		gd.addMessage( "Advanced values for channel " + channel.getName() );
		gd.addNumericField( "Radius_1", defaultRadius1[ channelId ], 0 );
		gd.addNumericField( "Radius_2", defaultRadius2[ channelId ], 0 );
		gd.addNumericField( "Threshold", defaultThreshold[ channelId ], 4 );
		gd.addCheckbox( "Find_minima", defaultFindMin[ channelId ] );
		gd.addCheckbox( "Find_maxima", defaultFindMax[ channelId ] );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.radius1[ channelId ] = defaultRadius1[ channelId ] = (int)Math.round( gd.getNextNumber() );
		this.radius2[ channelId ] = defaultRadius2[ channelId ] = (int)Math.round( gd.getNextNumber() );
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = gd.getNextNumber();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = gd.getNextBoolean();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues( final Channel channel )
	{
		final ViewId view = getViewSelection( "Interactive Difference-of-Mean", "Please select view to use for channel " + channel.getName(), channel );
		
		if ( view == null )
			return false;

		final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( view.getTimePointId(), view.getViewSetupId() );
		
		if ( !viewDescription.isPresent() )
		{
			IOFunctions.println( "You defined the view you selected as not present at this timepoint." );
			IOFunctions.println( "timepoint: " + viewDescription.getTimePoint().getName() + 
								 " angle: " + viewDescription.getViewSetup().getAngle().getName() + 
								 " channel: " + viewDescription.getViewSetup().getChannel().getName() + 
								 " illum: " + viewDescription.getViewSetup().getIllumination().getName() );
			return false;
		}
		
		RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > img = 
				spimData.getSequenceDescription().getImgLoader().getImage( viewDescription, false );
		
		if ( img == null )
		{
			IOFunctions.println( "View not found: " + viewDescription );
			return false;
		}
		
		final ImagePlus imp = ImageJFunctions.wrapFloat( img, "" ).duplicate();
		img = null;
		imp.setDimensions( 1, imp.getStackSize(), 1 );
		imp.setTitle( "tp: " + viewDescription.getTimePoint().getName() + " viewSetup: " + viewDescription.getViewSetupId() );		
		imp.show();		
		imp.setSlice( imp.getStackSize() / 2 );	
		
		final InteractiveIntegral ii = new InteractiveIntegral();
		final int channelId = channel.getId();	
		
		ii.setInitialRadius( Math.round( defaultRadius1[ channelId ] ) );
		ii.setThreshold( (float)defaultThreshold[ channelId ] );
		ii.setLookForMinima( defaultFindMin[ channelId ] );
		ii.setLookForMaxima( defaultFindMax[ channelId ] );
		
		ii.run( null );
		
		while ( !ii.isFinished() )
			SimpleMultiThreading.threadWait( 100 );
		
		imp.close();
			
		this.radius1[ channelId ] = defaultRadius1[ channelId ] = ii.getRadius1();
		this.radius2[ channelId ] = defaultRadius2[ channelId ] = ii.getRadius2();
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = ii.getThreshold();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = ii.getLookForMinima();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = ii.getLookForMaxima();

		return true;
	}
	

	@Override
	protected void init( final int numChannels )
	{
		radius1 = new int[ numChannels ];
		radius2 = new int[ numChannels ];
		threshold = new double[ numChannels ];
		findMin = new boolean[ numChannels ];
		findMax = new boolean[ numChannels ];

		if ( defaultRadius1 == null || defaultRadius1.length != numChannels )
		{
			defaultRadius1 = new int[ numChannels ];
			defaultRadius2 = new int[ numChannels ];
			defaultThreshold = new double[ numChannels ];
			defaultFindMin = new boolean[ numChannels ];
			defaultFindMax = new boolean[ numChannels ];
			
			for ( int c = 0; c < numChannels; ++c )
			{
				defaultRadius1[ c ] = defaultR1;
				defaultRadius2[ c ] = defaultR2;
				defaultThreshold[ c ] = defaultT;
				defaultFindMin[ c ] = false;
				defaultFindMax[ c ] = true;
			}
		}
	}
}
