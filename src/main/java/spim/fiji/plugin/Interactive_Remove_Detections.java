package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.removedetections.InteractiveProjections;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class Interactive_Remove_Detections implements PlugIn
{
	public static int defaultAngleChoice = 0;
	public static int defaultChannelChoice = 0;
	public static int defaultIlluminationChoice = 0;
	public static int defaultTimepointChoice = 0;

	public static int defaultProjectionChoice = 0;
	public static int defaultLabel = 0;

	public static String defaultNewLabel = "Manually removed";

	protected static String[] projectionChoice = new String[]{ "XY (Z-Projection)", "XZ (Y-Projection)", "YZ (X-Projection)" };

	@Override
	public void run( final String arg0 )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Interactively remove detections", false, false, false, false ) )
			return;

		final GenericDialog gd = new GenericDialog( "Select View" );

		final List< TimePoint > timepoints = result.getTimePointsToProcess();
		final String[] timepointNames = new String[ timepoints.size() ];
		for ( int i = 0; i < timepointNames.length; ++i )
			timepointNames[ i ] = result.getTimePointsToProcess().get( i ).getName();
		
		final List< Angle > angles = result.getData().getSequenceDescription().getAllAnglesOrdered();
		final String[] angleNames = new String[ angles.size() ];
		for ( int i = 0; i < angles.size(); ++i )
			angleNames[ i ] = angles.get( i ).getName();

		final List< Channel > channels = result.getData().getSequenceDescription().getAllChannelsOrdered();
		final String[] channelNames = new String[ channels.size() ];
		for ( int i = 0; i < channels.size(); ++i )
			channelNames[ i ] = channels.get( i ).getName();

		final List< Illumination > illuminations = result.getData().getSequenceDescription().getAllIlluminationsOrdered();
		final String[] illuminationNames = new String[ illuminations.size() ];
		for ( int i = 0; i < illuminations.size(); ++i )
			illuminationNames[ i ] = illuminations.get( i ).getName();

		gd.addChoice( "Angle", angleNames, angleNames[ defaultAngleChoice ] );
		gd.addChoice( "Channel", channelNames, channelNames[ defaultChannelChoice ] );
		gd.addChoice( "Illumination", illuminationNames, illuminationNames[ defaultIlluminationChoice ] );
		gd.addChoice( "Timepoint", timepointNames, timepointNames[ defaultTimepointChoice ] );
		gd.addMessage( "" );
		gd.addChoice( "Projection", projectionChoice, projectionChoice[ defaultProjectionChoice ] );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		final Angle angle = angles.get( defaultAngleChoice = gd.getNextChoiceIndex() );
		final Channel channel = channels.get( defaultChannelChoice = gd.getNextChoiceIndex() );
		final Illumination illumination = illuminations.get( defaultIlluminationChoice = gd.getNextChoiceIndex() );
		final TimePoint tp = timepoints.get( defaultTimepointChoice = gd.getNextChoiceIndex() );

		final int projection = defaultProjectionChoice = gd.getNextChoiceIndex();

		// get the corresponding viewid
		final ViewId viewId = SpimData2.getViewId( result.getData().getSequenceDescription(), tp, channel, angle, illumination );
		final String name = "angle: " + angle.getName() + " channel: " + channel.getName() + " illum: " + illumination.getName() + " timepoint: " + tp.getName();

		// this happens only if a viewsetup is not present in any timepoint
		// (e.g. after appending fusion to a dataset)
		if ( viewId == null )
		{
			IOFunctions.println( "This ViewSetup is not present for this timepoint: angle: " + name );
			return;
		}
		
		// get the viewdescription
		final ViewDescription viewDescription = result.getData().getSequenceDescription().getViewDescription( 
				viewId.getTimePointId(), viewId.getViewSetupId() );

		// check if this viewid is present in the current timepoint
		if ( !viewDescription.isPresent() )
		{
			IOFunctions.println( "This ViewSetup is not present for this timepoint: angle: " + name );
			return;
		}

		// XY == along z(2)
		// XZ == along y(1)
		// yz == along x(0)
		final int projectionDim = 2 - projection;

		final Pair< String, String > labels = queryLabelAndNewLabel( result.getData(), viewDescription );

		if ( labels == null )
			return;

		if ( !removeDetections( result.getData(), viewDescription, projectionDim, labels.getA(), labels.getB() ) )
			return;

		// now save it
		SpimData2.saveXML( result.getData(), result.getXMLFileName(), result.getClusterExtension() );
	}

	public static Pair< String, String > queryLabelAndNewLabel( final SpimData2 spimData, final ViewDescription vd )
	{
		final ViewInterestPoints interestPoints = spimData.getViewInterestPoints();
		final ViewInterestPointLists lists = interestPoints.getViewInterestPointLists( vd );

		if ( lists.getHashMap().keySet().size() == 0 )
		{
			IOFunctions.println(
					"No interest points available for angle: " + vd.getViewSetup().getAngle().getName() +
					" channel: " + vd.getViewSetup().getChannel().getName() +
					" illum: " + vd.getViewSetup().getIllumination().getName() +
					" timepoint: " + vd.getTimePoint().getName() );
			return null;
		}

		final String[] labels = new String[ lists.getHashMap().keySet().size() ];

		int i = 0;
		for ( final String label : lists.getHashMap().keySet() )
			labels[ i++ ] = label;

		if ( defaultLabel >= labels.length )
			defaultLabel = 0;

		Arrays.sort( labels );

		final GenericDialog gd = new GenericDialog( "Select Interest Points To Remove" );

		gd.addChoice( "Interest_Point_Label", labels, labels[ defaultLabel ]);
		gd.addStringField( "New_Label", defaultNewLabel, 20 );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return null;

		final String label = labels[ defaultLabel = gd.getNextChoiceIndex() ];
		final String newLabel = gd.getNextString();

		return new ValuePair< String, String>( label, newLabel );
	}

	public static boolean removeDetections( final SpimData2 spimData, final ViewDescription vd, final int projectionDim, final String label, final String newLabel )
	{
		final ViewInterestPoints interestPoints = spimData.getViewInterestPoints();
		final ViewInterestPointLists lists = interestPoints.getViewInterestPointLists( vd );

		final InteractiveProjections ip = new InteractiveProjections( spimData, vd, label, newLabel, projectionDim );

		do
		{
			try
			{
				Thread.sleep( 100 );
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		while ( ip.isRunning() );

		if ( ip.wasCanceled() )
			return false;

		final List< InterestPoint > ipList = ip.getInterestPointList();

		if ( ipList.size() == 0 )
		{
			IOFunctions.println( "No detections remaining. Quitting." );
			return false;
		}

		// add new label
		final InterestPointList newIpl = new InterestPointList(
				lists.getInterestPointList( label ).getBaseDir(),
				new File(
						lists.getInterestPointList( label ).getFile().getParentFile(),
						"tpId_" + vd.getTimePointId() + "_viewSetupId_" + vd.getViewSetupId() + "." + newLabel ) );

		newIpl.setInterestPoints( ipList );
		newIpl.setParameters( "manually removed detections from '" +label + "'" );
		newIpl.saveInterestPoints();

		lists.addInterestPointList( newLabel, newIpl );

		return true;
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new Interactive_Remove_Detections().run( null );
	}
}
