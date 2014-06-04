package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
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
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.process.fusion.export.DisplayImage;
import spim.process.interestpointregistration.ChannelProcess;

public class Visualize_Detections implements PlugIn
{
	protected static String[] detectionsChoice = new String[]{ "All detections", "Corresponding detections" };
	public static int defaultDetections = 0;
	public static boolean defaultDisplayInput = true;
	
	@Override
	public void run( final String arg0 )
	{
		// ask for everything but the channels
		final XMLParseResult result = new LoadParseQueryXML().queryXML( "visualize detections", true, false, true, true );
		
		if ( result == null )
			return;

		// ask which channels have the objects we are searching for
		final List< Channel > channels = result.getData().getSequenceDescription().getAllChannelsOrdered();

		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose segmentations to display" );

		if ( Interest_Point_Registration.defaultChannelLabels == null || Interest_Point_Registration.defaultChannelLabels.length != channels.size() )
			Interest_Point_Registration.defaultChannelLabels = new int[ channels.size() ];

		// check which channels and labels are available and build the choices
		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int j = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel( result.getData(), result.getTimePointsToProcess(), channel, "visualize" );
			
			if ( channelLabels == null )
				return;
			
			if ( Interest_Point_Registration.defaultChannelLabels[ channel.getId() ] >= labels.length )
				Interest_Point_Registration.defaultChannelLabels[ channel.getId() ] = 0;
			
			gd.addChoice( "Interest_points_channel_" + channel.getName(), labels, labels[ Interest_Point_Registration.defaultChannelLabels[ j++ ] ] );
			channelLabels.add( labels );
		}
		
		gd.addChoice( "Display", detectionsChoice, detectionsChoice[ defaultDetections ] );
		gd.addCheckbox( "Display_input_images", defaultDisplayInput );
		
		GUIHelper.addWebsite( gd );
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		// assemble which channels have been selected with with label
		final ArrayList< ChannelProcess > channelsToProcess = new ArrayList< ChannelProcess >();
		j = 0;
		
		for ( final Channel channel : channels )
		{
			final int channelChoice = Interest_Point_Registration.defaultChannelLabels[ channel.getId() ] = gd.getNextChoiceIndex();
			
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
			return;
		}
		
		for ( final ChannelProcess c : channelsToProcess )
			IOFunctions.println( "displaying channel: " + c.getChannel().getId()  + " label: '" + c.getLabel() + "'" );
		
		final int detections = defaultDetections = gd.getNextChoiceIndex();
		final boolean displayInput = defaultDisplayInput = gd.getNextBoolean();
		
		//
		// load the images and render the segmentations
		//
		
		final DisplayImage di = new DisplayImage();
		
		for ( final TimePoint t : result.getTimePointsToProcess() )
			for ( final ChannelProcess c : channelsToProcess )
				for ( final Illumination i : result.getIlluminationsToProcess() )
					for ( final Angle a : result.getAnglesToProcess() )
					{
						final ViewId viewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c.getChannel(), a, i );
						
						// get the viewdescription
						final ViewDescription viewDescription = result.getData().getSequenceDescription().getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );

						// check if the view is present
						if ( !viewDescription.isPresent() )
							continue;

						// load and display
						final String name = "TP" + t.getName() + "_Ch" + c.getChannel().getName() + "(label='" + c.getLabel() + "')_ill" + i.getName() + "_angle" + a.getName();
						final Interval interval;
						
						if ( displayInput )
						{
							@SuppressWarnings( "unchecked" )
							final RandomAccessibleInterval< UnsignedShortType > img = ( RandomAccessibleInterval< UnsignedShortType > ) result.getData().getSequenceDescription().getImgLoader().getImage( viewDescription );							
							di.exportImage( img, name );
							interval = img;
						}
						else
						{
							if ( !viewDescription.getViewSetup().hasSize() )
							{
								IOFunctions.println( "Cannot load image dimensions from XML for " + name + ", using min/max of all detections instead." );
								interval = null;
							}
							else
							{
								interval = new FinalInterval( viewDescription.getViewSetup().getSize() );
							}
						}
						
						di.exportImage( renderSegmentations( result.getData(), viewId, c.getLabel(), detections, interval ), "seg of " + name );
					}
	}
	
	protected Img< FloatType > renderSegmentations(
			final SpimData2 data,
			final ViewId viewId,
			final String label,
			final int detections,
			Interval interval )
	{		
		final InterestPointList ipl = data.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label );
		
		if ( ipl.getInterestPoints() == null || ipl.getInterestPoints().size() == 0 )
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
	
		final Img< FloatType > s = new ImagePlusImgFactory< FloatType >().create( interval, new FloatType() );
		final RandomAccess< FloatType > r = Views.extendZero( s ).randomAccess();
		
		final int n = s.numDimensions();
		final int[] tmp = new int[ n ];
		
		if ( detections == 0 )
		{
			IOFunctions.println( "Visualizing " + ipl.getInterestPoints().size() + " detections." );
			
			for ( final InterestPoint ip : ipl.getInterestPoints() )
			{
				for ( int d = 0; d < n; ++d )
					tmp[ d ] = Math.round( ip.getL()[ d ] );
	
				r.setPosition( tmp );
				r.get().set( 1 );
			}
		}
		else
		{
			final HashMap< Integer, InterestPoint > map = new HashMap< Integer, InterestPoint >();
			
			for ( final InterestPoint ip : ipl.getInterestPoints() )
				map.put( ip.getId(), ip );
			
			if ( ipl.getCorrespondingInterestPoints() == null || ipl.getCorrespondingInterestPoints().size() == 0 )
				ipl.loadCorrespondingInterestPoints();

			IOFunctions.println( "Visualizing " + ipl.getCorrespondingInterestPoints().size() + " corresponding detections." );
			
			for ( final CorrespondingInterestPoints ip : ipl.getCorrespondingInterestPoints() )
			{	
				for ( int d = 0; d < n; ++d )
					tmp[ d ] = Math.round( map.get( ip.getDetectionId() ).getL()[ d ] );
	
				r.setPosition( tmp );
				r.get().set( 1 );
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
