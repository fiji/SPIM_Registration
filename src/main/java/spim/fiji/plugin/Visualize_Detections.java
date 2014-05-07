package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.process.fusion.export.DisplayImage;
import spim.process.interestpointregistration.ChannelProcess;

public class Visualize_Detections implements PlugIn
{

	@Override
	public void run( final String arg0 )
	{
		// ask for everything but the channels
		final XMLParseResult result = new LoadParseQueryXML().queryXML( "visualize detections", true, false, true, true );
		
		if ( result == null )
			return;

		// ask which channels have the objects we are searching for
		final ArrayList< Channel > channels = result.getData().getSequenceDescription().getAllChannels();

		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose segmentations to display" );

		if ( Interest_Point_Registration.defaultChannelLabels == null || Interest_Point_Registration.defaultChannelLabels.length != channels.size() )
			Interest_Point_Registration.defaultChannelLabels = new int[ channels.size() ];

		// check which channels and labels are available and build the choices
		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int j = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel( result.getData(), result.getTimePointsToProcess(), channel );
			
			if ( channelLabels == null )
				return;
			
			if ( Interest_Point_Registration.defaultChannelLabels[ channel.getId() ] >= labels.length )
				Interest_Point_Registration.defaultChannelLabels[ channel.getId() ] = 0;
			
			gd.addChoice( "Interest_points_channel_" + channel.getName(), labels, labels[ Interest_Point_Registration.defaultChannelLabels[ j++ ] ] );
			channelLabels.add( labels );
		}
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
				String label = channelLabels.get( j++ )[ channelChoice ];
				
				if ( label.contains( Interest_Point_Registration.warningLabel ) )
					label = label.substring( 0, label.indexOf( Interest_Point_Registration.warningLabel ) );
				
				channelsToProcess.add( new ChannelProcess( channel, label ) );
			}
		}
		
		if ( channelsToProcess.size() == 0 )
		{
			IOFunctions.println( "No channels selected. Quitting." );
			return;
		}
		
		for ( final ChannelProcess c : channelsToProcess )
			IOFunctions.println( "registering channel: " + c.getChannel().getId()  + " label: '" + c.getLabel() + "'" );
		
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
						final ViewDescription< TimePoint, ViewSetup > viewDescription = result.getData().getSequenceDescription().getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );

						// check if the view is present
						if ( !viewDescription.isPresent() )
							continue;

						// load and display
						final RandomAccessibleInterval< UnsignedShortType > img = result.getData().getSequenceDescription().getImgLoader().getUnsignedShortImage( viewDescription );
						final String name = "TP" + t.getName() + "_Ch" + c.getChannel().getName() + "(label='" + c.getLabel() + "')_ill" + i.getName() + "_angle" + a.getName(); 
						
						di.exportImage( img, name );
						di.exportImage( renderSegmentations( result.getData(), viewId, c.getLabel(), img ), "seg of " + name );
					}
	}
	
	protected Img< FloatType > renderSegmentations(
			final SpimData2 data,
			final ViewId viewId,
			final String label,
			final Interval img )
	{		
		final InterestPointList ipl = data.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label );
		
		if ( ipl.getInterestPoints() == null || ipl.getInterestPoints().size() == 0 )
			ipl.loadInterestPoints();
	
		final Img< FloatType > s = new ImagePlusImgFactory< FloatType >().create( img, new FloatType() );
		final RandomAccess< FloatType > r = Views.extendZero( s ).randomAccess();
		
		final int n = s.numDimensions();
		final int[] tmp = new int[ n ];
		
		IOFunctions.println( "Visualizing " + ipl.getInterestPoints().size() + " detections." );
		
		for ( final InterestPoint ip : ipl.getInterestPoints() )
		{
			for ( int d = 0; d < n; ++d )
				tmp[ d ] = Math.round( ip.getL()[ d ] );

			r.setPosition( tmp );
			r.get().set( 1 );
		}
		

		try
		{
			Gauss3.gauss( new double[]{ 2, 2, 2 }, Views.extendZero( s ), s );
		}
		catch (IncompatibleTypeException e)
		{
			e.printStackTrace();
		}
		
		return s;
	}
	
	public static void main( final String[] args )
	{
		new ImageJ();
		new Visualize_Detections().run( null );
	}

}
