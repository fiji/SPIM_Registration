package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.img.Img;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.process.fusion.export.DisplayImage;
import spim.process.interestpointregistration.ChannelProcess;

public class Visualize_Detections implements PlugIn
{
	public static String[] detectionsChoice = new String[]{ "All detections", "Corresponding detections" };
	public static int defaultDetections = 0;
	public static double defaultDownsample = 1.0;
	public static boolean defaultDisplayInput = false;

	public static class Params
	{
		final public ArrayList< ChannelProcess > channelsToProcess;
		final public int detections;
		final public double downsample;
		final public boolean displayInput;

		public Params( final ArrayList< ChannelProcess > channelsToProcess, final int detections, final double downsample, final boolean displayInput )
		{
			this.channelsToProcess = channelsToProcess;
			this.detections = detections;
			this.downsample = downsample;
			this.displayInput = displayInput;
		}
	}
	
	@Override
	public void run( final String arg0 )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "visualize detections", true, false, true, true ) )
			return;

		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );
		final Params params = queryDetails( result.getData(), viewIds );

		if ( params != null )
			visualize( result.getData(), viewIds, params.channelsToProcess, params.detections, params.downsample, params.displayInput );
	}

	public static Params queryDetails( final SpimData2 spimData, final List< ViewId > viewIds )
	{
		// ask which channels have the objects we are searching for
		final List< Channel > channels = spimData.getSequenceDescription().getAllChannelsOrdered();
		final int nAllChannels = spimData.getSequenceDescription().getAllChannelsOrdered().size();

		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose segmentations to display" );

		if ( Interest_Point_Registration.defaultChannelLabels == null || Interest_Point_Registration.defaultChannelLabels.length != nAllChannels )
			Interest_Point_Registration.defaultChannelLabels = new int[ nAllChannels ];

		// check which channels and labels are available and build the choices
		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int j = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel( spimData, viewIds, channel, "visualize" );
			
			if ( labels == null )
				return null;
			
			if ( Interest_Point_Registration.defaultChannelLabels[ j ] >= labels.length )
				Interest_Point_Registration.defaultChannelLabels[ j ] = 0;

			String ch = channel.getName().replace( ' ', '_' );
			gd.addChoice( "Interest_points_channel_" + ch, labels, labels[ Interest_Point_Registration.defaultChannelLabels[ j++ ] ] );
			channelLabels.add( labels );
		}
		
		gd.addChoice( "Display", detectionsChoice, detectionsChoice[ defaultDetections ] );
		gd.addNumericField( "Downsample_detections_rendering", defaultDownsample, 2, 4, "times" );
		gd.addCheckbox( "Display_input_images", defaultDisplayInput );
		
		GUIHelper.addWebsite( gd );
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;
		
		// assemble which channels have been selected with with label
		final ArrayList< ChannelProcess > channelsToProcess = new ArrayList< ChannelProcess >();
		j = 0;
		
		for ( final Channel channel : channels )
		{
			final int channelChoice = Interest_Point_Registration.defaultChannelLabels[ j ] = gd.getNextChoiceIndex();
			
			if ( channelChoice < channelLabels.get( j ).length - 1 )
			{
				String label = channelLabels.get( j )[ channelChoice ];
				
				if ( label.contains( Interest_Point_Registration.warningLabel ) )
					label = label.substring( 0, label.indexOf( Interest_Point_Registration.warningLabel ) );
				
				channelsToProcess.add( new ChannelProcess( channel, label ) );
			}

			++j;
		}
		
		if ( channelsToProcess.size() == 0 )
		{
			IOFunctions.println( "No channels selected. Quitting." );
			return null;
		}
		
		for ( final ChannelProcess c : channelsToProcess )
			IOFunctions.println( "displaying channel: " + c.getChannel().getId()  + " label: '" + c.getLabel() + "'" );
		
		final int detections = defaultDetections = gd.getNextChoiceIndex();
		final double downsample = defaultDownsample = gd.getNextNumber();
		final boolean displayInput = defaultDisplayInput = gd.getNextBoolean();

		return new Params( channelsToProcess, detections, downsample, displayInput );
	}

	public static void visualize(
			final SpimData2 spimData,
			final List< ViewId > viewIds,
			final ArrayList< ChannelProcess > channelsToProcess,
			final int detections,
			final double downsample,
			final boolean displayInput )
	{
		//
		// load the images and render the segmentations
		//
		final DisplayImage di = new DisplayImage();

		for ( final ViewId viewId : viewIds )
			for ( final ChannelProcess c : channelsToProcess )
			{
				// get the viewdescription
				final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId.getTimePointId(), viewId.getViewSetupId() );

				// check if the view is present
				if ( !vd.isPresent() || vd.getViewSetup().getChannel().getId() != c.getChannel().getId() )
					continue;

				// load and display
				final String name = "TP" + vd.getTimePointId() + "_Ch" + c.getChannel().getName() + "(label='" + c.getLabel() + "')_ill" + vd.getViewSetup().getIllumination().getName() + "_angle" + vd.getViewSetup().getAngle().getName();
				final Interval interval;
				
				if ( displayInput )
				{
					@SuppressWarnings( "unchecked" )
					final RandomAccessibleInterval< UnsignedShortType > img = ( RandomAccessibleInterval< UnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( vd.getViewSetupId() ).getImage( vd.getTimePointId() );
					di.exportImage( img, name );
					interval = img;
				}
				else
				{
					if ( !vd.getViewSetup().hasSize() )
					{
						IOFunctions.println( "Cannot load image dimensions from XML for " + name + ", using min/max of all detections instead." );
						interval = null;
					}
					else
					{
						interval = new FinalInterval( vd.getViewSetup().getSize() );
					}
				}
				
				di.exportImage( renderSegmentations( spimData, viewId, c.getLabel(), detections, interval, downsample ), "seg of " + name );
			}
	}
	
	protected static Img< UnsignedShortType > renderSegmentations(
			final SpimData2 data,
			final ViewId viewId,
			final String label,
			final int detections,
			Interval interval,
			final double downsample )
	{		
		final InterestPointList ipl = data.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label );
		
		if ( ipl.getInterestPoints() == null )
			ipl.loadInterestPoints();
			
		if ( interval == null )
		{
			final int n = ipl.getInterestPoints().get( 0 ).getL().length;
			
			final long[] min = new long[ n ];
			final long[] max = new long[ n ];

			for ( int d = 0; d < n; ++d )
			{
				min[ d ] = Math.round( ipl.getInterestPoints().get( 0 ).getL()[ d ] ) - 1;
				max[ d ] = Math.round( ipl.getInterestPoints().get( 0 ).getL()[ d ] ) + 1;
			}
			
			for ( final InterestPoint ip : ipl.getInterestPoints() )
			{
				for ( int d = 0; d < n; ++d )
				{
					min[ d ] = Math.min( min[ d ], Math.round( ip.getL()[ d ] ) - 1 );
					max[ d ] = Math.max( max[ d ], Math.round( ip.getL()[ d ] ) + 1 );
				}
			}
			
			interval = new FinalInterval( min, max );
		}
		
		// downsample
		final long[] min = new long[ interval.numDimensions() ];
		final long[] max = new long[ interval.numDimensions() ];
		
		for ( int d = 0; d < interval.numDimensions(); ++d )
		{
			min[ d ] = Math.round( interval.min( d ) / downsample );
			max[ d ] = Math.round( interval.max( d ) / downsample ) ;
		}
		
		interval = new FinalInterval( min, max );
	
		final Img< UnsignedShortType > s = new ImagePlusImgFactory< UnsignedShortType >().create( interval, new UnsignedShortType() );
		final RandomAccess< UnsignedShortType > r = Views.extendZero( s ).randomAccess();
		
		final int n = s.numDimensions();
		final long[] tmp = new long[ n ];
		
		if ( detections == 0 )
		{
			IOFunctions.println( "Visualizing " + ipl.getInterestPoints().size() + " detections." );
			
			for ( final InterestPoint ip : ipl.getInterestPoints() )
			{
				for ( int d = 0; d < n; ++d )
					tmp[ d ] = Math.round( ip.getL()[ d ] / downsample );
	
				r.setPosition( tmp );
				r.get().set( 65535 );
			}
		}
		else
		{
			final HashMap< Integer, InterestPoint > map = new HashMap< Integer, InterestPoint >();
			
			for ( final InterestPoint ip : ipl.getInterestPoints() )
				map.put( ip.getId(), ip );
			
			if ( ipl.getCorrespondingInterestPoints() == null )
			{
				if ( !ipl.loadCorrespondingInterestPoints() )
				{
					IOFunctions.println( "No corresponding detections available, the dataset was not registered using these detections." );
					return s;
				}
			}

			IOFunctions.println( "Visualizing " + ipl.getCorrespondingInterestPoints().size() + " corresponding detections." );
			
			for ( final CorrespondingInterestPoints ip : ipl.getCorrespondingInterestPoints() )
			{	
				for ( int d = 0; d < n; ++d )
					tmp[ d ] = Math.round( map.get( ip.getDetectionId() ).getL()[ d ] / downsample );
	
				r.setPosition( tmp );
				r.get().set( 65535 );
			}
		}

		try
		{
			Gauss3.gauss( new double[]{ 2, 2, 2 }, Views.extendZero( s ), s );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Gaussian Convolution of detections failed: " + e );
			e.printStackTrace();
		}
		catch ( OutOfMemoryError e )
		{
			IOFunctions.println( "Gaussian Convolution of detections failed due to out of memory, just showing plain image: " + e );
		}
		
		return s;
	}
	
	public static void main( final String[] args )
	{
		new ImageJ();
		new Visualize_Detections().run( null );
	}

}
