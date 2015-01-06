package spim.fiji.plugin;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RealPoint;
import net.imglib2.KDTree;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.thinout.ChannelProcessThinOut;
import spim.fiji.plugin.thinout.Histogram;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class ThinOut_Detections implements PlugIn
{
	public static boolean[] defaultShowHistogram;
	public static int[] defaultSubSampling;
	public static String[] defaultNewLabels;
	public static int[] defaultRemoveKeep;
	public static double[] defaultCutoffThresholdMin, defaultCutoffThresholdMax;

	public static String[] removeKeepChoice = new String[]{ "Remove Range", "Keep Range" };
	public static double defaultThresholdMinValue = 0;
	public static double defaultThresholdMaxValue = 5;
	public static int defaultSubSamplingValue = 1;
	public static String defaultNewLabelText = "thinned-out";
	public static int defaultRemoveKeepValue = 0; // 0 == remove, 1 == keep

	@Override
	public void run( final String arg )
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "", true, false, true, true ) )
			return;

		// ask which channels have the objects we are searching for
		final List< ChannelProcessThinOut > channels = getChannelsAndLabels(
				xml.getData(),
				xml.getTimePointsToProcess(),
				xml.getData().getSequenceDescription().getAllChannelsOrdered(),
				xml.getIlluminationsToProcess(),
				xml.getAnglesToProcess() );

		if ( channels == null )
			return;

		// get the actual min/max thresholds for cutting out
		if ( !getThinOutThresholds(
				xml.getData(),
				xml.getTimePointsToProcess(),
				channels,
				xml.getIlluminationsToProcess(),
				xml.getAnglesToProcess() ) )
			return;

		// thin out detections and save the new interestpoint files
		if ( !thinOut(
				xml.getData(),
				xml.getTimePointsToProcess(),
				channels,
				xml.getIlluminationsToProcess(),
				xml.getAnglesToProcess() ) )
			return;

		// write new xml
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		
		final String xmlFile = new File( xml.getData().getBasePath(), new File( xml.getXMLFileName() ).getName() ).getAbsolutePath();
		try 
		{
			io.save( xml.getData(), xmlFile );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + xmlFile + "'." );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + xmlFile + "': " + e );
			e.printStackTrace();
		}
	}

	protected boolean thinOut(
			final SpimData2 spimData,
			final List< TimePoint > timePointsToProcess,
			final List< ChannelProcessThinOut > channels,
			final List< Illumination > illumsToProcess,
			final List< Angle > anglesToProcess )
	{
		final ViewInterestPoints vip = spimData.getViewInterestPoints();

		for ( final ChannelProcessThinOut channel : channels )
		{
			final double minDistance = channel.getMin();
			final double maxDistance = channel.getMax();
			final boolean keepRange = channel.keepRange();

			for ( final TimePoint t : timePointsToProcess )
			{
				for ( final Illumination i : illumsToProcess )
				{
					for ( final Angle a : anglesToProcess )
					{
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, channel.getChannel(), a, i );

						// this happens only if a viewsetup is not present in any timepoint
						// (e.g. after appending fusion to a dataset)
						if ( viewId == null )
							continue;

						final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
						
						if ( !vd.isPresent() )
							continue;

						final ViewInterestPointLists vipl = vip.getViewInterestPointLists( viewId );
						final InterestPointList oldIpl = vipl.getInterestPointList( channel.getLabel() );

						if ( oldIpl.getInterestPoints() == null || oldIpl.getInterestPoints().size() == 0 )
							oldIpl.loadInterestPoints();

						final VoxelDimensions voxelSize = vd.getViewSetup().getVoxelSize();

						// assemble the list of points (we need two lists as the KDTree sorts the list)
						// we assume that the order of list2 and points is preserved!
						final List< RealPoint > list1 = new ArrayList< RealPoint >();
						final List< RealPoint > list2 = new ArrayList< RealPoint >();
						final List< float[] > points = new ArrayList< float[] >();

						for ( final InterestPoint ip : oldIpl.getInterestPoints() )
						{
							list1.add ( new RealPoint(
									ip.getL()[ 0 ] * voxelSize.dimension( 0 ),
									ip.getL()[ 1 ] * voxelSize.dimension( 1 ),
									ip.getL()[ 2 ] * voxelSize.dimension( 2 ) ) );

							list2.add ( new RealPoint(
									ip.getL()[ 0 ] * voxelSize.dimension( 0 ),
									ip.getL()[ 1 ] * voxelSize.dimension( 1 ),
									ip.getL()[ 2 ] * voxelSize.dimension( 2 ) ) );

							points.add( ip.getL() );
						}

						// make the KDTree
						final KDTree< RealPoint > tree = new KDTree< RealPoint >( list1, list1 );

						// Nearest neighbor for each point, populate the new list
						final KNearestNeighborSearchOnKDTree< RealPoint > nn = new KNearestNeighborSearchOnKDTree< RealPoint >( tree, 2 );
						final InterestPointList newIpl = new InterestPointList(
								oldIpl.getBaseDir(),
								new File(
										oldIpl.getFile().getParentFile(),
										"tpId_" + viewId.getTimePointId() + "_viewSetupId_" + viewId.getViewSetupId() + "." + channel.getNewLabel() ) );

						int id = 0;
						for ( int j = 0; j < list2.size(); ++j )
						{
							final RealPoint p = list2.get( j );
							nn.search( p );
							
							// first nearest neighbor is the point itself, we need the second nearest
							final double d = nn.getDistance( 1 );
							
							if ( ( keepRange && d >= minDistance && d <= maxDistance ) || ( !keepRange && ( d < minDistance || d > maxDistance ) ) )
							{
								newIpl.getInterestPoints().add( new InterestPoint( id++, points.get( j ).clone() ) );
							}
						}

						if ( keepRange )
							newIpl.setParameters( "thinned-out '" + channel.getLabel() + "', kept range from " + minDistance + " to " + maxDistance );
						else
							newIpl.setParameters( "thinned-out '" + channel.getLabel() + "', removed range from " + minDistance + " to " + maxDistance );

						vipl.addInterestPointList( channel.getNewLabel(), newIpl );

						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": TP=" + t.getId() + " ViewSetup=" + vd.getViewSetupId() + 
								", Detections: " + oldIpl.getInterestPoints().size() + " >>> " + newIpl.getInterestPoints().size() );

						if ( !newIpl.saveInterestPoints() )
						{
							IOFunctions.println( "Error saving interest point list: " + new File( newIpl.getBaseDir(), newIpl.getFile().toString() + newIpl.getInterestPointsExt() ) );
							return false;
						}
					}
				}
			}
		}
			
		return true;
	}

	protected boolean getThinOutThresholds(
			final SpimData2 spimData,
			final List< TimePoint > timePointsToProcess,
			final List< ChannelProcessThinOut > channels,
			final List< Illumination > illumsToProcess,
			final List< Angle > anglesToProcess )
	{
		for ( final ChannelProcessThinOut channel : channels )
			if ( channel.showHistogram() )
				plotHistogram( spimData, timePointsToProcess, channel, illumsToProcess, anglesToProcess );

		if ( defaultCutoffThresholdMin == null || defaultCutoffThresholdMin.length != channels.size() || 
				defaultCutoffThresholdMax == null || defaultCutoffThresholdMax.length != channels.size() )
		{
			defaultCutoffThresholdMin = new double[ channels.size() ];
			defaultCutoffThresholdMax = new double[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
			{
				defaultCutoffThresholdMin[ i ] = defaultThresholdMinValue;
				defaultCutoffThresholdMax[ i ] = defaultThresholdMaxValue;
			}
		}

		if ( defaultRemoveKeep == null || defaultRemoveKeep.length != channels.size() )
		{
			defaultRemoveKeep = new int[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				defaultRemoveKeep[ i ] = defaultRemoveKeepValue;
		}

		final GenericDialog gd = new GenericDialog( "Define cut-off threshold" );

		for ( int c = 0; c < channels.size(); ++c )
		{
			final ChannelProcessThinOut channel = channels.get( c );
			gd.addChoice( "Channel_" + channel.getChannel().getName() + "_", removeKeepChoice, removeKeepChoice[ defaultRemoveKeep[ c ] ] );
			gd.addNumericField( "Channel_" + channel.getChannel().getName() + "_range_lower_threshold", defaultCutoffThresholdMin[ c ], 2 );
			gd.addNumericField( "Channel_" + channel.getChannel().getName() + "_range_upper_threshold", defaultCutoffThresholdMax[ c ], 2 );
			gd.addMessage( "" );
		}

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		for ( int c = 0; c < channels.size(); ++c )
		{
			final ChannelProcessThinOut channel = channels.get( c );

			final int removeKeep = defaultRemoveKeep[ c ] = gd.getNextChoiceIndex();
			if ( removeKeep == 1 )
				channel.setKeepRange( true );
			else
				channel.setKeepRange( false );
			channel.setMin( defaultCutoffThresholdMin[ c ] = gd.getNextNumber() );
			channel.setMax( defaultCutoffThresholdMax[ c ] = gd.getNextNumber() );

			if ( channel.getMin() >= channel.getMax() )
			{
				IOFunctions.println( "You selected the minimal threshold larger than the maximal threshold for channel " + channel.getChannel().getName() );
				IOFunctions.println( "Stopping." );
				return false;
			}
			else
			{
				if ( channel.keepRange() )
					IOFunctions.println( "Channel " + channel.getChannel().getName() + ": keep only distances from " + channel.getMin() + " >>> " + channel.getMax() );
				else
					IOFunctions.println( "Channel " + channel.getChannel().getName() + ": remove distances from " + channel.getMin() + " >>> " + channel.getMax() );
			}
		}

		return true;
	}

	protected Histogram plotHistogram(
			final SpimData2 spimData,
			final List< TimePoint > timePointsToProcess,
			final ChannelProcessThinOut channel,
			final List< Illumination > illumsToProcess,
			final List< Angle > anglesToProcess )
	{
		final ViewInterestPoints vip = spimData.getViewInterestPoints();

		// list of all distances
		final ArrayList< Double > distances = new ArrayList< Double >();
		final Random rnd = new Random( System.currentTimeMillis() );
		String unit = null;

		for ( final TimePoint t : timePointsToProcess )
			for ( final Illumination i : illumsToProcess )
				for ( final Angle a : anglesToProcess )
				{
					final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, channel.getChannel(), a, i );

					// this happens only if a viewsetup is not present in any timepoint
					// (e.g. after appending fusion to a dataset)
					if ( viewId == null )
						continue;
					
					final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
					
					if ( !vd.isPresent() )
						continue;

					final ViewInterestPointLists vipl = vip.getViewInterestPointLists( viewId );
					final InterestPointList ipl = vipl.getInterestPointList( channel.getLabel() );

					final VoxelDimensions voxelSize = vd.getViewSetup().getVoxelSize();

					if ( ipl.getInterestPoints() == null || ipl.getInterestPoints().size() == 0 )
						ipl.loadInterestPoints();

					if ( unit == null )
						unit = vd.getViewSetup().getVoxelSize().unit();

					// assemble the list of points
					final List< RealPoint > list = new ArrayList< RealPoint >();

					for ( final InterestPoint ip : ipl.getInterestPoints() )
					{
						list.add ( new RealPoint(
								ip.getL()[ 0 ] * voxelSize.dimension( 0 ),
								ip.getL()[ 1 ] * voxelSize.dimension( 1 ),
								ip.getL()[ 2 ] * voxelSize.dimension( 2 ) ) );
					}

					// make the KDTree
					final KDTree< RealPoint > tree = new KDTree< RealPoint >( list, list );

					// Nearest neighbor for each point
					final KNearestNeighborSearchOnKDTree< RealPoint > nn = new KNearestNeighborSearchOnKDTree< RealPoint >( tree, 2 );

					for ( final RealPoint p : list )
					{
						// every n'th point only
						if ( rnd.nextDouble() < 1.0 / (double)channel.getSubsampling() )
						{
							nn.search( p );
							
							// first nearest neighbor is the point itself, we need the second nearest
							distances.add( nn.getDistance( 1 ) );
						}
					}
				}

		final Histogram h = new Histogram( distances, 100, "Distance Histogram [Channel=" + channel.getChannel().getName() + "]", unit  );
		h.showHistogram();
		IOFunctions.println( "Channel " + channel.getChannel().getName() + ": min distance=" + h.getMin() + ", max distance=" + h.getMax() );
		return h;
	}

	protected ArrayList< ChannelProcessThinOut > getChannelsAndLabels(
			final SpimData2 spimData,
			final List< TimePoint > timePointsToProcess,
			final List< Channel > channels,
			final List< Illumination > illumsToProcess,
			final List< Angle > anglesToProcess )
	{
		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose segmentations to thin out" );

		if ( Interest_Point_Registration.defaultChannelLabels == null || Interest_Point_Registration.defaultChannelLabels.length != channels.size() )
			Interest_Point_Registration.defaultChannelLabels = new int[ channels.size() ];

		if ( defaultShowHistogram == null || defaultShowHistogram.length != channels.size() )
		{
			defaultShowHistogram = new boolean[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				defaultShowHistogram[ i ] = true;
		}

		if ( defaultSubSampling == null || defaultSubSampling.length != channels.size() )
		{
			defaultSubSampling = new int[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				defaultSubSampling[ i ] = defaultSubSamplingValue;
		}

		if ( defaultNewLabels == null || defaultNewLabels.length != channels.size() )
		{
			defaultNewLabels = new String[ channels.size() ];
			for ( int i = 0; i < channels.size(); ++i )
				defaultNewLabels[ i ] = defaultNewLabelText;
		}

		// check which channels and labels are available and build the choices
		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int j = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = Interest_Point_Registration.getAllInterestPointLabelsForChannel(
					spimData,
					timePointsToProcess,
					anglesToProcess,
					illumsToProcess,
					channel,
					"thin out" );

			if ( Interest_Point_Registration.defaultChannelLabels[ j ] >= labels.length )
				Interest_Point_Registration.defaultChannelLabels[ j ] = 0;

			gd.addCheckbox( "Channel_" + channel.getName() + ":_Display_distance_histogram", defaultShowHistogram[ j ] );
			gd.addChoice( "Channel_" + channel.getName() + ":_Interest_points", labels, labels[ Interest_Point_Registration.defaultChannelLabels[ j ] ] );
			gd.addStringField( "Channel_" + channel.getName() + ":_New_label", defaultNewLabels[ j ], 20 );
			gd.addNumericField( "Channel_" + channel.getName() + ":_Subsample histogram", defaultSubSampling[ j ], 0, 5, "times" );

			channelLabels.add( labels );
			++j;
		}

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		// assemble which channels have been selected with with label
		final ArrayList< ChannelProcessThinOut > channelsToProcess = new ArrayList< ChannelProcessThinOut >();
		j = 0;
		
		for ( final Channel channel : channels )
		{
			final boolean showHistogram = defaultShowHistogram[ j ] = gd.getNextBoolean();
			final int channelChoice = Interest_Point_Registration.defaultChannelLabels[ j ] = gd.getNextChoiceIndex();
			final String newLabel = defaultNewLabels[ j ] = gd.getNextString();
			final int subSampling = defaultSubSampling[ j ] = (int)Math.round( gd.getNextNumber() );
			
			if ( channelChoice < channelLabels.get( j ).length - 1 )
			{
				String label = channelLabels.get( j )[ channelChoice ];

				if ( label.contains( Interest_Point_Registration.warningLabel ) )
					label = label.substring( 0, label.indexOf( Interest_Point_Registration.warningLabel ) );

				channelsToProcess.add( new ChannelProcessThinOut( channel, label, newLabel, showHistogram, subSampling ) );
			}

			++j;
		}

		return channelsToProcess;
	}

	public static void main( final String[] args )
	{
		new ThinOut_Detections().run( null );
	}
}
