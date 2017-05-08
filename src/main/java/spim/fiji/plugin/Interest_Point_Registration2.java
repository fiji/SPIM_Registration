package spim.fiji.plugin;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.models.RigidModel3D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.plugin.interestpointregistration.pairwise.CenterOfMassGUI;
import spim.fiji.plugin.interestpointregistration.pairwise.GeometricHashingGUI;
import spim.fiji.plugin.interestpointregistration.pairwise.IterativeClosestPointGUI;
import spim.fiji.plugin.interestpointregistration.pairwise.PairwiseGUI;
import spim.fiji.plugin.interestpointregistration.pairwise.RGLDMGUI;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.interestpointregistration.TransformationTools;
import spim.process.interestpointregistration.pairwise.PairwiseStrategyTools;

/**
*
* @author Stephan Preibisch (stephan.preibisch@gmx.de)
*
*/
public class Interest_Point_Registration2 implements PlugIn
{
	public static ArrayList< PairwiseGUI > staticPairwiseAlgorithms = new ArrayList< PairwiseGUI >();

	public static String[] registrationTypes = {
		"Register timepoints individually", 
		"Match against one reference timepoint (no global optimization)", 
		"All-to-all timepoints matching (global optimization)", 
		"All-to-all timepoints matching with range ('reasonable' global optimization)" };
	
	public enum RegistrationType { TIMEPOINTS_INDIVIDUALLY, TO_REFERENCE_TIMEPOINT, ALL_TO_ALL, ALL_TO_ALL_WITH_RANGE };

	public static String[] fixViewsChoice = new String[]{
		"Fix first view",
		"Select fixed view",
		"Do not fix viewss" };

	public static String[] mapBackChoice = new String[]{
		"Do not map back (use this if views are fixed)",
		"Map back to first view using translation model",
		"Map back to first view using rigid model",
		"Map back to user defined view using translation model",
		"Map back to user defined view using rigid model" };

	public final static String warningLabel = " (WARNING: Only available for "; 

	static
	{
		IOFunctions.printIJLog = true;
		staticPairwiseAlgorithms.add( new GeometricHashingGUI() );
		staticPairwiseAlgorithms.add( new RGLDMGUI() );
		staticPairwiseAlgorithms.add( new CenterOfMassGUI() );
		staticPairwiseAlgorithms.add( new IterativeClosestPointGUI() );
	}

	// basic dialog
	public static int defaultAlgorithm = 0;
	public static int defaultRegistrationType = 0;
	public static int[] defaultChannelLabels = null;
	private class BasicRegistrationParamerters
	{
		PairwiseGUI pwr;
		RegistrationType registrationType;
		HashMap< Channel, String > channelProcess;
	}

	// advanced dialog
	public static int defaultRange = 5;
	public static int defaultReferenceTimepointIndex = -1;
	public static boolean defaultConsiderTimepointAsUnit = false;
	public static int defaultFixViews = 0;
	public static int defaultMapBack = 0;
	public static boolean defaultShowStatistics = true;
	private class AdvancedRegistrationParamerters
	{
		int range, referenceTimePoint, fixViewsIndex, mapBackIndex;
		boolean considerTimepointsAsUnit, showStatistics;
	}
	
	// fix and map back dialog
	//public static boolean defaultSameFixedViews = true;
	//public static boolean defaultSameReferenceView = true;

	//public static boolean[] defaultFixedViews = null;
	//public static int defaultReferenceView = 0;

	@Override
	public void run( final String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "for performing interest point registration", true, false, true, true ) )
			return;

		register(
			result.getData(),
			SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ),
			result.getClusterExtension(),
			result.getXMLFileName(),
			true );
	}

	public boolean register(
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		return register( data, viewIds, "", null, false );
	}

	public boolean register(
			final SpimData2 data,
			final List< ViewId > viewIds,
			final String xmlFileName,
			final boolean saveXML )
	{
		return register( data, viewIds, "", xmlFileName, saveXML );
	}

	public boolean register(
			final SpimData2 data,
			final List< ViewId > viewIds,
			final String clusterExtension,
			final String xmlFileName,
			final boolean saveXML )
	{
		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( data, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// which timepoints are part of the 
		final List< TimePoint > timepointToProcess = SpimData2.getAllTimePointsSorted( data, viewIds );
		final int nAllTimepoints = data.getSequenceDescription().getTimePoints().size();

		// ask which channels have the objects we are searching for
		final List< Channel > channels = SpimData2.getAllChannelsSorted( data, viewIds );
		final int nAllChannels = data.getSequenceDescription().getAllChannelsOrdered().size();

		// query basic registration parameters
		final BasicRegistrationParamerters brp = basicRegistrationParameters( timepointToProcess, nAllTimepoints, channels, nAllChannels, data, viewIds );

		if ( brp == null )
			return false;

		final AdvancedRegistrationParamerters arp = advancedRegistrationParameters( brp, timepointToProcess, data, viewIds );

		if ( arp == null )
			return false;

		return true;
	}

	public boolean processRegistration(
			final BasicRegistrationParamerters brp,
			final AdvancedRegistrationParamerters arp,
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		// collect corresponding current transformations
		final Map< ViewId, ViewRegistration > transformations = data.getViewRegistrations().getViewRegistrations();

		// get the corresponding interest point lists
		final Map< ViewId, ViewInterestPointLists > vipl = data.getViewInterestPoints().getViewInterestPoints();
		final Map< ViewId, InterestPointList > interestpointLists = new HashMap< ViewId, InterestPointList >();

		for ( final ViewId viewId : viewIds )
		{
			final Channel channel = data.getSequenceDescription().getViewDescription( viewId ).getViewSetup().getChannel();
			final String label = brp.channelProcess.get( channel );
			interestpointLists.put( viewId, vipl.get( viewId ).getInterestPointList( label ) );
		}

		// load & transform all interest points
		final Map< ViewId, List< InterestPoint > > interestpoints = TransformationTools.getAllTransformedInterestPoints(
				viewIds,
				transformations,
				interestpointLists );

		// define fixed tiles
		final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
		fixedViews.add( viewIds.get( 0 ) );

		// define groups
		//final ArrayList< ArrayList< ViewId > > groupedViews = new ArrayList< ArrayList< ViewId > >();

		// define all pairs depending on the registrationtype
		//final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.allToAll( viewIds, fixedViews, groupedViews );

		// set the fixed tiles and the potential mapping back to some tile
		//if ( !setFixedTilesAndReference( fixViews, mapBack, brp.registrationType ) )
		//	return false;

		return true;
	}

	/**
	 * Assign the right fixed tiles and reference tiles for this type of optimization
	 * 
	 * @param fixViewsIndex - "Fix first views", "Select fixed view", "Do not fix views"
	 * @param mapBackIndex - 
	 * 				"Do not map back (use this if tiles are fixed)",
	 * 				"Map back to first view using translation model",
	 * 				"Map back to first view using rigid model",
	 * 				"Map back to user defined view using translation model",
	 * 				"Map back to user defined view using rigid model"
	 * @param type - the type of registration used
	 */
	public boolean setFixedTilesAndReference( final int fixViewsIndex, final int mapBackIndex, final RegistrationType type )
	{
		final List< GlobalOptimizationSubset > subsets = type.getAllViewPairs();

		//
		// define fixed tiles
		//
		final Set< ViewId > fixedTiles = new HashSet< ViewId >();

		if ( fixTilesIndex == 0 ) // all first tiles
		{
			for ( final GlobalOptimizationSubset subset : subsets )
			{
				if ( subset.getViews().size() == 0 )
					IOFunctions.println( "Nothing to do for: " + subset.getDescription() + ". No tiles fixed." );
				else
					fixedTiles.add( subset.getViews().get( 0 ) );
			}
		}
		else if ( fixTilesIndex == 1 )
		{
			// select fixed tiles (this assumes that one subset consists of one timepoint)
			if ( subsets.size() > 1 )
			{
				final GenericDialog gd1 = new GenericDialog( "Type of manual choice" );
				gd1.addCheckbox( "Same_fixed_view(s) for each timepoint", defaultSameFixedViews );

				gd1.showDialog();
				if ( gd1.wasCanceled() )
					return false;

				if ( defaultSameFixedViews = gd1.getNextBoolean() )
				{
					// check which viewsetups are available in all subsets
					final ArrayList< ViewSetup > setupList = getListOfViewSetupPresentInAllSubsets( subsets, type );

					if ( setupList.size() == 0 )
					{
						IOFunctions.println( "No Viewsetup is available in all Timepoints." );
						return false;
					}

					if ( defaultFixedTiles == null || defaultFixedTiles.length != setupList.size() )
						defaultFixedTiles = new boolean[ setupList.size() ];

					final GenericDialog gd2 = new GenericDialog( "Select ViewSetups to be fixed for each of the timepoints" );

					for ( int i = 0; i < setupList.size(); ++i )
					{
						final ViewSetup vs = setupList.get( i );
						gd2.addCheckbox( "Angle_" + vs.getAngle().getName() + "_Channel_" + vs.getChannel().getName() + "_Illum_" + vs.getIllumination().getName(), defaultFixedTiles[ i ] );
					}

					GUIHelper.addScrollBars( gd2 );

					gd2.showDialog();
					if ( gd2.wasCanceled() )
						return false;

					for ( int i = 0; i < setupList.size(); ++i )
						if ( defaultFixedTiles[ i ] = gd2.getNextBoolean() )
							for ( final GlobalOptimizationSubset subset : subsets )
								fixedTiles.add( new ViewId( subset.getViews().get( 0 ).getTimePointId(), setupList.get( i ).getId() ) );
				}
				else
				{
					for ( final GlobalOptimizationSubset subset : subsets )
						if ( !askForFixedTiles( subset, type, fixedTiles, "Select fixed ViewIds for timepoint " + subset.getViews().get( 0 ).getTimePointId() ) )
							return false;
				}
			}
			else
			{
				// there is just one subset
				if ( !askForFixedTiles( subsets.get( 0 ), type, fixedTiles, "Select fixed ViewIds" ) )
					return false;
			}
		}
		else
		{
			// no fixed tiles or reference timepoint
		}

		if ( fixTilesIndex >= 0 )
			type.setFixedTiles( fixedTiles );

		IOFunctions.println( "Following tiles are fixed:" );
		for ( final ViewId id : type.getFixedTiles() )
		{
			final ViewDescription vd = type.getSpimData().getSequenceDescription().getViewDescription( id );
			final ViewSetup vs = vd.getViewSetup();

			IOFunctions.println( "Angle:" + vs.getAngle().getName() + " Channel:" + vs.getChannel().getName() + " Illum:" + vs.getIllumination().getName() + " TimePoint:" + vd.getTimePoint().getId() );
		}

		//
		// now the reference tile(s)
		//
		if ( mapBackIndex == 0 )
		{
			type.setMapBackModel( null );
			type.setMapBackReferenceTiles( new HashMap< GlobalOptimizationSubset, ViewId >() );
		}
		else if ( mapBackIndex == 1 || mapBackIndex == 3 )
		{
			type.setMapBackModel( new TranslationModel3D() );
		}
		else
		{
			type.setMapBackModel( new RigidModel3D() );
		}

		if ( mapBackIndex == 1 || mapBackIndex == 2 )
		{
			for ( final GlobalOptimizationSubset subset : subsets )
				type.setMapBackReferenceTile( subset, subset.getViews().get( 0 ) );
		}
		else if ( mapBackIndex == 3 || mapBackIndex == 4 )
		{
			// select reference tile
			// select fixed tiles (this assumes that one subset consists of one timepoint)
			if ( subsets.size() > 1 )
			{
				final GenericDialog gd1 = new GenericDialog( "Type of manual choice" );
				gd1.addCheckbox( "Same_reference_view(s) for each timepoint", defaultSameReferenceView );

				gd1.showDialog();
				if ( gd1.wasCanceled() )
					return false;

				if ( defaultSameReferenceView = gd1.getNextBoolean() )
				{
					// check which viewsetups are available in all subsets
					final ArrayList< ViewSetup > setupList = getListOfViewSetupPresentInAllSubsets( subsets, type );

					if ( setupList.size() == 0 )
					{
						IOFunctions.println( "No Viewsetup is available in all Timepoints." );
						return false;
					}

					final GenericDialog gd2 = new GenericDialog( "Select Reference ViewSetup each of the timepoints" );

					final String[] choices = new String[ setupList.size() ];
					
					for ( int i = 0; i < setupList.size(); ++ i )
					{
						final ViewSetup vs = setupList.get( i );
						choices[ i ] = "Angle_" + vs.getAngle().getName() + "_Channel_" + vs.getChannel().getName() + "_Illum_" + vs.getIllumination().getName();
					}

					if ( defaultReferenceTile >= choices.length )
						defaultReferenceTile = 0;

					gd2.addChoice( "Select_Reference_ViewSetup", choices, choices[ defaultReferenceTile ] );

					gd2.showDialog();
					if ( gd2.wasCanceled() )
						return false;

					final int index = defaultReferenceTile = gd2.getNextChoiceIndex();

					for ( final GlobalOptimizationSubset subset : subsets )
						type.setMapBackReferenceTile( subset, new ViewId( subset.getViews().get( 0 ).getTimePointId(), setupList.get( index ).getId() ) );
				}
				else
				{
					for ( final GlobalOptimizationSubset subset : subsets )
						if ( !askForReferenceTile( subset, type, "Select Reference Views" ) )
							return false;
				}
			}
			else
			{
				
				if ( !askForReferenceTile( subsets.get( 0 ), type, "Select Reference View" ) )
					return false;
			}
		}
		else
		{
			// no reference tile
		}

		IOFunctions.println( "Following tiles are reference tiles (for mapping back if there are no fixed tiles):" );
		for ( final GlobalOptimizationSubset subset : subsets )
		{
			final ViewId id = type.getMapBackReferenceTile( subset );
			if ( id != null )
			{
				final ViewDescription vd = type.getSpimData().getSequenceDescription().getViewDescription( id );
				final ViewSetup vs = vd.getViewSetup();

				IOFunctions.println( "Angle:" + vs.getAngle().getName() + " Channel:" + vs.getChannel().getName() + " Illum:" + vs.getIllumination().getName() + " TimePoint:" + vd.getTimePoint().getId() );
			}
		}
			
		return true;
	}

	public AdvancedRegistrationParamerters advancedRegistrationParameters(
			final BasicRegistrationParamerters brp,
			final List< TimePoint > timepointToProcess,
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		final GenericDialog gd = new GenericDialog( "Register: " + registrationTypes[ brp.registrationType.ordinal() ] );

		if ( brp.registrationType == RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			// assemble all timepoints, each one could be a reference
			final String[] tpList = assembleTimepoints( data.getSequenceDescription().getTimePoints() );
			
			// by default, the reference timepoint is the first one
			if ( defaultReferenceTimepointIndex < 0 || defaultReferenceTimepointIndex >= tpList.length )
				defaultReferenceTimepointIndex = 0;

			gd.addChoice( "Reference timepoint", tpList, tpList[ defaultReferenceTimepointIndex ] );
			gd.addMessage( "" );
		}
		else if ( brp.registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
		{
			gd.addSlider( "Range for all-to-all timepoint matching", 2, 10, defaultRange );
		}

		// for all registrations that include multiple timepointss
		if ( brp.registrationType != RegistrationType.TIMEPOINTS_INDIVIDUALLY )
		{
			gd.addCheckbox( "Consider_each_timepoint_as_rigid_unit", defaultConsiderTimepointAsUnit );
			gd.addMessage( "Note: This option applies the same transformation model to all views of one timepoint. This makes for example\n" +
					"sense if all timepoints are individually pre-registered using an affine transformation model, and for the timeseries\n" +
					"stabilization a translation model should be used.\n ", GUIHelper.smallStatusFont );
		}

		// whenever it is not a registration to a reference timepoint choose potentially fixed tiles
		// (otherwise all views of the reference are fixed)
		if ( brp.registrationType != RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			gd.addChoice( "Fix_views", fixViewsChoice, fixViewsChoice[ defaultFixViews ] );
			gd.addChoice( "Map_back_views", mapBackChoice, mapBackChoice[ defaultMapBack ] );
		}

		gd.addMessage( "" );
		gd.addMessage( "Algorithm parameters [" + brp.pwr.getDescription() + "]", new Font( Font.SANS_SERIF, Font.BOLD, 12 ) );
		gd.addMessage( "" );

		brp.pwr.addQuery( gd );

		if ( timepointToProcess.size() > 1 )
			gd.addCheckbox( "Show_timeseries_statistics", defaultShowStatistics );

		// display the dialog
		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		final AdvancedRegistrationParamerters arp = new AdvancedRegistrationParamerters();

		// assign default numbers even if not necessary
		arp.referenceTimePoint = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 ).getId();
		arp.range = defaultRange;

		if ( brp.registrationType == RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			arp.referenceTimePoint = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( defaultReferenceTimepointIndex = gd.getNextChoiceIndex() ).getId();

			// check that at least one of the views of the reference timepoint is part of the viewdescriptions
			boolean contains = false;

			for ( final ViewId viewId : viewIds )
				if ( viewId.getTimePointId() == arp.referenceTimePoint )
					contains = true;

			if ( !contains )
			{
				IOFunctions.println( "No views of the reference timepoint are part of the registration." );
				IOFunctions.println( "Please re-run and select the corresponding views that should be used as reference." );

				return null;
			}
		}

		if ( brp.registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
			arp.range = defaultRange = (int)Math.round( gd.getNextNumber() );

		if ( brp.registrationType != RegistrationType.TIMEPOINTS_INDIVIDUALLY )
			arp.considerTimepointsAsUnit = defaultConsiderTimepointAsUnit = gd.getNextBoolean();
		else
			arp.considerTimepointsAsUnit = false;

		if ( brp.registrationType != RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			arp.fixViewsIndex = defaultFixViews = gd.getNextChoiceIndex();
			arp.mapBackIndex = defaultMapBack = gd.getNextChoiceIndex();
		}
		else
		{
			arp.fixViewsIndex = arp.mapBackIndex = -1;
		}

		if ( !brp.pwr.parseDialog( gd ) )
			return null;

		if ( timepointToProcess.size() > 1 )
			defaultShowStatistics = arp.showStatistics = gd.getNextBoolean();
		else
			arp.showStatistics = false;

		return arp;
	}

	public BasicRegistrationParamerters basicRegistrationParameters(
			final List< TimePoint > timepointToProcess,
			final int nAllTimepoints,
			final List< Channel > channels,
			final int nAllChannels,
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		final GenericDialog gd = new GenericDialog( "Basic Registration Parameters" );

		// the GenericDialog needs a list[] of String for the algorithms that can register
		final String[] descriptions = new String[ staticPairwiseAlgorithms.size() ];
		
		for ( int i = 0; i < staticPairwiseAlgorithms.size(); ++i )
			descriptions[ i ] = staticPairwiseAlgorithms.get( i ).getDescription();
		
		if ( defaultAlgorithm >= descriptions.length )
			defaultAlgorithm = 0;

		gd.addChoice( "Registration_algorithm", descriptions, descriptions[ defaultAlgorithm ] );

		final String[] choicesGlobal;
		if ( timepointToProcess.size() > 1 )
			choicesGlobal = registrationTypes.clone();
		else
			choicesGlobal = new String[]{ registrationTypes[ 0 ] };

		if ( defaultRegistrationType >= choicesGlobal.length )
			defaultRegistrationType = 0;

		gd.addChoice( "Type_of_registration", choicesGlobal, choicesGlobal[ defaultRegistrationType ] );

		if ( defaultChannelLabels == null || defaultChannelLabels.length != nAllChannels )
			defaultChannelLabels = new int[ nAllChannels ];

		// check which channels and labels are available and build the choices
		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int i = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = getAllInterestPointLabelsForChannel( data, viewIds, channel, "register" );

			if ( defaultChannelLabels[ channel.getId() ] >= labels.length )
				defaultChannelLabels[ channel.getId() ] = 0;

			gd.addChoice( "Interest_points_channel_" + channel.getName(), labels, labels[ defaultChannelLabels[ i++ ] ] );
			channelLabels.add( labels );
		}

		// assemble the last registration names of all viewsetups involved
		final HashMap< String, Integer > names = GUIHelper.assembleRegistrationNames( data, viewIds );
		gd.addMessage( "" );
		GUIHelper.displayRegistrationNames( gd, names );
		gd.addMessage( "" );

		GUIHelper.addWebsite( gd );

		if ( names.keySet().size() > 5 )
			GUIHelper.addScrollBars( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		final int algorithm = defaultAlgorithm = gd.getNextChoiceIndex();

		final RegistrationType registrationType;

		switch ( defaultRegistrationType = gd.getNextChoiceIndex() )
		{
			case 0:
				registrationType = RegistrationType.TIMEPOINTS_INDIVIDUALLY;
				break;
			case 1:
				registrationType = RegistrationType.TO_REFERENCE_TIMEPOINT;
				break;
			case 2:
				registrationType = RegistrationType.ALL_TO_ALL;
				break;
			case 3:
				registrationType = RegistrationType.ALL_TO_ALL_WITH_RANGE;
				break;
			default:
				return null;
		}

		// assemble which channels have been selected with with label
		final HashMap< Channel, String > channelsToProcess = new HashMap< Channel, String >();
		i = 0;

		for ( final Channel channel : channels )
		{
			final int channelChoice = defaultChannelLabels[ channel.getId() ] = gd.getNextChoiceIndex();
			
			if ( channelChoice < channelLabels.get( i ).length - 1 )
			{
				String label = channelLabels.get( i )[ channelChoice ];
				
				if ( label.contains( warningLabel ) )
					label = label.substring( 0, label.indexOf( warningLabel ) );
				
				channelsToProcess.put( channel, label );
			}
			++i;
		}
		
		if ( channelsToProcess.size() == 0 )
		{
			IOFunctions.println( "No channels selected. Quitting." );
			return null;
		}

		for ( final Channel c : channelsToProcess.keySet() )
			IOFunctions.println( "registering channel: " + c.getId()  + " label: '" + channelsToProcess.get( c ) + "'" );

		final PairwiseGUI pwr = staticPairwiseAlgorithms.get( algorithm ).newInstance();

		IOFunctions.println( "Registration algorithm: " + pwr.getDescription() );
		IOFunctions.println( "Registration type: " + registrationType.name() );
		IOFunctions.println( "Channels to process: " + channelsToProcess.size() );

		final BasicRegistrationParamerters brp = new BasicRegistrationParamerters();
		brp.pwr = pwr;
		brp.registrationType = registrationType;
		brp.channelProcess = channelsToProcess;

		return brp;
	}

	protected static String[] assembleTimepoints( final TimePoints timepoints )
	{
		final String[] tps = new String[ timepoints.size() ];

		for ( int t = 0; t < tps.length; ++t )
			tps[ t ] = timepoints.getTimePointsOrdered().get( t ).getName();

		return tps;
	}

	/**
	 * Goes through all ViewDescriptions and checks all available labels for interest point detection
	 * 
	 * @param spimData
	 * @param channel
	 * @return
	 */
	public static String[] getAllInterestPointLabelsForChannel(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final Channel channel )
	{
		return getAllInterestPointLabelsForChannel( spimData, viewIdsToProcess, channel, null );
	}

	/**
	 * Goes through all ViewDescriptions and checks all available labels for interest point detection
	 * 
	 * @param spimData
	 * @param channel
	 * @param doWhat - the text for not doing anything with this channel
	 * @return
	 */
	public static String[] getAllInterestPointLabelsForChannel(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final Channel channel,
			final String doWhat )
	{
		final ViewInterestPoints interestPoints = spimData.getViewInterestPoints();
		final HashMap< String, Integer > labels = new HashMap< String, Integer >();
		
		int countViewDescriptions = 0;

		for ( final ViewId viewId : viewIdsToProcess )
		{
			// get the viewdescription
			final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
					viewId.getTimePointId(), viewId.getViewSetupId() );

			// check if the view is present
			if ( !viewDescription.isPresent() || viewDescription.getViewSetup().getChannel().getId() != channel.getId() )
				continue;
			
			// which lists of interest points are available
			final ViewInterestPointLists lists = interestPoints.getViewInterestPointLists( viewId );
			
			for ( final String label : lists.getHashMap().keySet() )
			{
				int count = 1;

				if ( labels.containsKey( label ) )
					count += labels.get( label );

				labels.put( label, count );
			}

			// are they available in all viewdescriptions?
			++countViewDescriptions;
		}
		
		final String[] allLabels;

		if ( doWhat == null )
			allLabels = new String[ labels.keySet().size() ];
		else
			allLabels = new String[ labels.keySet().size() + 1 ];
		
		int i = 0;
		
		for ( final String label : labels.keySet() )
		{
			allLabels[ i ] = label;

			if ( labels.get( label ) != countViewDescriptions )
				allLabels[ i ] += warningLabel + labels.get( label ) + "/" + countViewDescriptions + " Views!)";

			++i;
		}

		if ( doWhat != null )
			allLabels[ i ] = "(DO NOT " + doWhat + " this channel)";

		return allLabels;
	}

}
