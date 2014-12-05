package spim.process.fusion.boundingbox;

import ij.gui.GenericDialog;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.imglib2.realtransform.AffineTransform3D;

import mpicbg.imglib.util.Util;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.plugin.Interest_Point_Registration;
import spim.fiji.plugin.Visualize_Detections;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.process.fusion.export.ImgExport;
import spim.process.interestpointregistration.ChannelProcess;

public class AutomaticReorientation extends ManualBoundingBox
{
	public static enum Reorientation { NONE, PARTLY, ALL };

	public static String reorientationDescription = "Reorientation to minimize bounding box";
	public String[] reorientationChoice = new String[]{
			"Automatically reorientate the sample (and store transformation for all views in the XML)",
			"Automatically reorientate the sample (and store transformation only for fused views in the XML)",
			"Automatically reorientate the sample (just temporarily, do NOT store transformation in XML)",
			"Do NOT reorientate the sample" };

	public static int defaultReorientate = 0;
	public static boolean defaultSaveReorientation = true;
	public static int defaultDetections = 1;
	public static double defaultPercentX = 20;
	public static double defaultPercentY = 20;
	public static double defaultPercentZ = 20;

	public AutomaticReorientation(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport )
	{
		GenericDialog gd = new GenericDialog( "Bounding Box Definition" );

		// ask for what do do
		final Pair< Integer, Integer > reorientated = numReorientated();

		String note;

		if ( reorientated.getA() == 0 )
			note = "None";
		else if ( reorientated.getA().intValue() == reorientated.getB().intValue() )
			note = "All";
		else
			note = reorientated.getA().intValue() + " of " + reorientated.getB().intValue();

		gd.addMessage( "Note: " + note + " of the views are already reorientated.", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addChoice( "Reorientation", reorientationChoice, reorientationChoice[ defaultReorientate ] );

		// ask which detections to use
		gd.addMessage( "" );
		gd.addMessage( "Note: The bounding box is estimated based on detections in the image, choose which ones to use.", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		// check which channels and labels are available and build the choices
		// ask which channels have the objects we are searching for
		final List< Channel > channels = spimData.getSequenceDescription().getAllChannelsOrdered();

		if ( Interest_Point_Registration.defaultChannelLabels == null || Interest_Point_Registration.defaultChannelLabels.length != channels.size() )
			Interest_Point_Registration.defaultChannelLabels = new int[ channels.size() ];

		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int j = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel(
					spimData,
					timepointsToProcess,
					anglesToProcess,
					illumsToProcess,
					channel,
					"use any detections from" );

			if ( labels == null )
				return false;

			if ( Interest_Point_Registration.defaultChannelLabels[ j ] >= labels.length )
				Interest_Point_Registration.defaultChannelLabels[ j ] = 0;
			
			gd.addChoice( "Interest_points_channel_" + channel.getName(), labels, labels[ Interest_Point_Registration.defaultChannelLabels[ j++ ] ] );
			channelLabels.add( labels );
		}

		gd.addChoice( "Use", Visualize_Detections.detectionsChoice, Visualize_Detections.detectionsChoice[ defaultDetections ] );

		// details about the bouding box
		gd.addMessage( "" );
		gd.addMessage(
				"Note: The detections themselves should not lie on the edge of the bounding box, please define how\n" +
				"much bigger [in percent] the bounding box should be. If you want to define it in pixels, use the\n" +
				"following dialog and put 0% here.", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addSlider( "Additional_size_x [%]", 0, 100, defaultPercentX );
		gd.addSlider( "Additional_size_y [%]", 0, 100, defaultPercentY );
		gd.addSlider( "Additional_size_z [%]", 0, 100, defaultPercentZ );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		final int reorientate = defaultReorientate = gd.getNextChoiceIndex();

		// assemble which channels have been selected with with label
		final ArrayList< ChannelProcess > channelsToUse = new ArrayList< ChannelProcess >();
		j = 0;

		for ( final Channel channel : channels )
		{
			final int channelChoice = Interest_Point_Registration.defaultChannelLabels[ channel.getId() ] = gd.getNextChoiceIndex();
			
			if ( channelChoice < channelLabels.get( j ).length - 1 )
			{
				String label = channelLabels.get( j )[ channelChoice ];
				
				if ( label.contains( Interest_Point_Registration.warningLabel ) )
					label = label.substring( 0, label.indexOf( Interest_Point_Registration.warningLabel ) );
				
				channelsToUse.add( new ChannelProcess( channel, label ) );
			}

			++j;
		}
		
		if ( channelsToUse.size() == 0 )
		{
			IOFunctions.println( "No channels selected. Quitting." );
			return false;
		}

		final int detections = defaultDetections = gd.getNextChoiceIndex();

		final double percentX = defaultPercentX = gd.getNextNumber();
		final double percentY = defaultPercentY = gd.getNextNumber();
		final double percentZ = defaultPercentZ = gd.getNextNumber();

		for ( final ChannelProcess c : channelsToUse )
			IOFunctions.println( "using from channel: " + c.getChannel().getId()  + " label: '" + c.getLabel() + "', " + (detections == 0 ? "all detections." : "only corresponding detections.") );

		
		if ( reorientate == 3 )
		{
			final Pair< float[], float[] > minmax = detemineSizeSimple( channelsToUse, detections );

			final float[] minF = minmax.getA();
			final float[] maxF = minmax.getB();
			final int[] min = new int[ minF.length ];
			final int[] max = new int[ maxF.length ];

			float addX = (maxF[ 0 ] - minF[ 0 ]) * (float)( percentX/100.0 ) / 2;
			float addY = (maxF[ 1 ] - minF[ 1 ]) * (float)( percentY/100.0 ) / 2;
			float addZ = (maxF[ 2 ] - minF[ 2 ]) * (float)( percentZ/100.0 ) / 2;

			min[ 0 ] = Math.round( minF[ 0 ] - addX );
			min[ 1 ] = Math.round( minF[ 1 ] - addY );
			min[ 2 ] = Math.round( minF[ 2 ] - addZ );

			max[ 0 ] = Math.round( maxF[ 0 ] + addX );
			max[ 1 ] = Math.round( maxF[ 1 ] + addY );
			max[ 2 ] = Math.round( maxF[ 2 ] + addZ );

			IOFunctions.println( "Min (with addition): " + Util.printCoordinates( min ) );
			IOFunctions.println( "Max (with addition): " + Util.printCoordinates( max ) );

			BoundingBox.defaultMin = min;
			BoundingBox.defaultMax = max;
		}
		else
		{
			new RuntimeException( "Not implemented yet." );
		}

		return super.queryParameters( fusion, imgExport );
	}

	protected Pair< float[], float[] > detemineSizeSimple( final ArrayList< ChannelProcess > channelsToUse, final int detections )
	{
		final List< float[] > points = getAllDetectionsInGlobalCoordinates( channelsToUse, detections );

		final float[] min = points.get( 0 ).clone();
		final float[] max = min.clone();

		for ( final float[] p : points )
			for ( int d = 0; d < p.length; ++d )
			{
				min[ d ] = Math.min( min[ d ], p[ d ] );
				max[ d ] = Math.max( max[ d ], p[ d ] );
			}

		IOFunctions.println( "Min (direct): " + Util.printCoordinates( min ) );
		IOFunctions.println( "Max (direct): " + Util.printCoordinates( max ) );

		return new ValuePair< float[], float[] >( min, max );
	}

	protected List< float[] > getAllDetectionsInGlobalCoordinates( final ArrayList< ChannelProcess > channelsToUse, final int detections )
	{
		final ArrayList< float[] > ipList = new ArrayList< float[] >();

		for ( final TimePoint t : timepointsToProcess )
			for ( final ChannelProcess c : channelsToUse )
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c.getChannel(), a, i );

						// this happens only if a viewsetup is not present in any timepoint
						// (e.g. after appending fusion to a dataset)
						if ( viewId == null )
							continue;

						final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
						
						if ( !vd.isPresent() )
							continue;

						// get the registration
						final ViewRegistration r = spimData.getViewRegistrations().getViewRegistration( viewId );
						r.updateModel();
						final AffineTransform3D transform = r.getModel();

						// get the list of detections
						final ViewInterestPointLists vipl = spimData.getViewInterestPoints().getViewInterestPointLists( viewId );
						final InterestPointList ipl = vipl.getInterestPointList( c.getLabel() );

						ipl.loadInterestPoints();

						final List< InterestPoint > list = ipl.getInterestPoints();

						// use all detections
						if ( detections == 0 )
						{
							for ( final InterestPoint p : list )
							{
								final float[] source = p.getL();
								final float[] target = new float[ source.length ];
								transform.apply( source, target );
								ipList.add( target );
							}
						}
						else // use only those who have correspondences
						{
							ipl.loadCorrespondingInterestPoints();

							final HashMap< Integer, InterestPoint > map = new HashMap< Integer, InterestPoint >();

							for ( final InterestPoint ip : list )
								map.put( ip.getId(), ip );

							final List< CorrespondingInterestPoints > list2 = ipl.getCorrespondingInterestPoints();

							for ( final CorrespondingInterestPoints cp : list2 )
							{
								final InterestPoint p = map.get( cp.getDetectionId() );
								final float[] source = p.getL();
								final float[] target = new float[ source.length ];
								transform.apply( source, target );
								ipList.add( target );
							}
						}
					}

		return ipList;
	}

	protected Pair< Integer, Integer > numReorientated()
	{
		final ViewRegistrations vrs = spimData.getViewRegistrations();

		int isReorientated = 0;
		int sumViews = 0;

		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );

						// this happens only if a viewsetup is not present in any timepoint
						// (e.g. after appending fusion to a dataset)
						if ( viewId == null )
							continue;

						final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
						
						if ( !vd.isPresent() )
							continue;

						final ViewRegistration vr = vrs.getViewRegistration( viewId );
						final ViewTransform vt = vr.getTransformList().get( 0 );

						++sumViews;

						if ( vt.hasName() && vt.getName().startsWith( reorientationDescription ) )
								++isReorientated;
					}

		return new ValuePair< Integer, Integer >( isReorientated, sumViews );
	}

	@Override
	public AutomaticReorientation newInstance(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess)
	{
		return new AutomaticReorientation( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Automatically reorientate and estimate (experimental)";
	}
}
