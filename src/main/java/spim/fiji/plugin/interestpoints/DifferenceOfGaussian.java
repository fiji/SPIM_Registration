package spim.fiji.plugin.interestpoints;

import ij.ImagePlus;
import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
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
import mpicbg.spim.segmentation.InteractiveDoG;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpoints.ProcessDOG;

public class DifferenceOfGaussian extends DifferenceOf
{
	public static double defaultS = 1.8;
	public static double defaultT = 0.008;

	public static double defaultSigma[];
	public static double defaultThreshold[];
	public static boolean defaultFindMin[];
	public static boolean defaultFindMax[];

	double[] sigma;
	double[] threshold;
	boolean[] findMin;
	boolean[] findMax;	

	@Override
	public String getDescription() { return "Difference-of-Gaussian"; }

	@Override
	public DifferenceOfGaussian newInstance() { return new DifferenceOfGaussian(); }

	@Override
	public HashMap< ViewId, List<Point> > findInterestPoints( final SpimData2 spimData, final ArrayList< Channel> channelsToProcess, final ArrayList< TimePoint > timepointsToProcess )
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
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );

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
						interestPoints.put( viewId, ProcessDOG.compute( img, (float)sigma[ c.getId() ], (float)threshold[ c.getId() ], localization, findMin[ c.getId() ], findMax[ c.getId() ] ) );
						img.close();

				        benchmark.computation += System.currentTimeMillis() - time2;
					}

		return interestPoints;
	}

	@Override
	protected boolean setDefaultValues( final Channel channel, final int brightness )
	{
		final int channelId = channel.getId();
		
		this.sigma[ channelId ] = defaultS;
		this.findMin[ channelId ] = false;
		this.findMax[ channelId ] = true;

		if ( brightness == 0 )
			this.threshold[ channelId ] = 0.001;
		else if ( brightness == 1 )
			this.threshold[ channelId ] = 0.008;
		else if ( brightness == 2 )
			this.threshold[ channelId ] = 0.03;
		else if ( brightness == 3 )
			this.threshold[ channelId ] = 0.1;
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
		gd.addNumericField( "Sigma", defaultSigma[ channelId ], 0 );
		gd.addNumericField( "Threshold", defaultThreshold[ channelId ], 4 );
		gd.addCheckbox( "Find_minima", defaultFindMin[ channelId ] );
		gd.addCheckbox( "Find_maxima", defaultFindMax[ channelId ] );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.sigma[ channelId ] = defaultSigma[ channelId ] = gd.getNextNumber();
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = gd.getNextNumber();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = gd.getNextBoolean();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues( final Channel channel )
	{
		final ViewId view = getViewSelection( "Interactive Difference-of-Gaussian", "Please select view to use for channel " + channel.getName(), channel );
		
		if ( view == null )
			return false;
		
		final ViewDescription<TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( view.getTimePointId(), view.getViewSetupId() );
		
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
		imp.setRoi( 0, 0, imp.getWidth()/3, imp.getHeight()/3 );		

		final InteractiveDoG idog = new InteractiveDoG();
		final int channelId = channel.getId();

		idog.setSigma2isAdjustable( false );
		idog.setInitialSigma( (float)defaultSigma[ channelId ] );
		idog.setThreshold( (float)defaultThreshold[ channelId ] );
		idog.setLookForMinima( defaultFindMin[ channelId ] );
		idog.setLookForMaxima( defaultFindMax[ channelId ] );

		idog.run( null );
		
		while ( !idog.isFinished() )
			SimpleMultiThreading.threadWait( 100 );
		
		imp.close();

		this.sigma[ channelId ] = defaultSigma[ channelId ] = idog.getInitialSigma();
		this.threshold[ channelId ] = defaultThreshold[ channelId ] = idog.getThreshold();
		this.findMin[ channelId ] = defaultFindMin[ channelId ] = idog.getLookForMinima();
		this.findMax[ channelId ] = defaultFindMax[ channelId ] = idog.getLookForMaxima();
		
		return true;
	}
	
	@Override
	protected void init( final int numChannels )
	{
		this.sigma = new double[ numChannels ];
		this.threshold = new double[ numChannels ];
		this.findMin = new boolean[ numChannels ];
		this.findMax = new boolean[ numChannels ];

		if ( defaultSigma == null || defaultSigma.length != numChannels )
		{
			defaultSigma = new double[ numChannels ];
			defaultThreshold = new double[ numChannels ];
			defaultFindMin = new boolean[ numChannels ];
			defaultFindMax = new boolean[ numChannels ];
			
			for ( int c = 0; c < numChannels; ++c )
			{
				defaultSigma[ c ] = defaultS;
				defaultThreshold[ c ] = defaultT;
				defaultFindMin[ c ] = false;
				defaultFindMax[ c ] = true;
			}
		}
	}
}
