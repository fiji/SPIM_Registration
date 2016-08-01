package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.models.RigidModel3D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.centerofmass.CenterOfMass;
import spim.process.interestpointregistration.geometricdescriptor.RGLDM;
import spim.process.interestpointregistration.geometrichashing.GeometricHashing;
import spim.process.interestpointregistration.icp.IterativeClosestPoint;
import spim.process.interestpointregistration.optimizationtypes.AllToAllRegistration;
import spim.process.interestpointregistration.optimizationtypes.AllToAllRegistrationWithRange;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;
import spim.process.interestpointregistration.optimizationtypes.IndividualTimepointRegistration;
import spim.process.interestpointregistration.optimizationtypes.ReferenceTimepointRegistration;
import spim.process.interestpointregistration.registrationstatistics.RegistrationStatistics;
import spim.process.interestpointregistration.registrationstatistics.TimeLapseDisplay;

/**
 *
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Interest_Point_Registration implements PlugIn
{
	public static ArrayList< InterestPointRegistration > staticAlgorithms = new ArrayList< InterestPointRegistration >();

	public static String[] registrationTypes = {
		"Register timepoints individually", 
		"Match against one reference timepoint (no global optimization)", 
		"All-to-all timepoints matching (global optimization)", 
		"All-to-all timepoints matching with range ('reasonable' global optimization)" };
	
	public enum RegistrationType { TIMEPOINTS_INDIVIDUALLY, TO_REFERENCE_TIMEPOINT, ALL_TO_ALL, ALL_TO_ALL_WITH_RANGE };

	public static String[] fixTilesChoice = new String[]{
		"Fix first tile",
		"Select fixed tile",
		"Do not fix tiles" };

	public static String[] mapBackChoice = new String[]{
		"Do not map back (use this if tiles are fixed)",
		"Map back to first tile using translation model",
		"Map back to first tile using rigid model",
		"Map back to user defined tile using translation model",
		"Map back to user defined tile using rigid model" };
	
	public static int defaultAlgorithm = 0;
	public static int defaultRegistrationType = 0;
	public static int[] defaultChannelLabels = null;
	public static int defaultRange = 5;
	public static int defaultReferenceTimepointIndex = -1;
	public static boolean defaultConsiderTimepointAsUnit = false;
	public static int defaultFixTiles = 0;
	public static int defaultMapBack = 0;
	public static boolean defaultSameFixedViews = true;
	public static boolean defaultSameReferenceView = true;

	public static boolean[] defaultFixedTiles = null;
	public static int defaultReferenceTile = 0;
	public static boolean defaultShowStatistics = true;

	public final static String warningLabel = " (WARNING: Only available for "; 
	
	static
	{
		IOFunctions.printIJLog = true;
		staticAlgorithms.add( new GeometricHashing( null, null, null ) );
		staticAlgorithms.add( new RGLDM( null, null, null ) );
		staticAlgorithms.add( new CenterOfMass( null, null, null ) );
		staticAlgorithms.add( new IterativeClosestPoint( null, null, null ) );
	}

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
		// the GenericDialog needs a list[] of String for the algorithms that can register
		final String[] descriptions = new String[ staticAlgorithms.size() ];
		
		for ( int i = 0; i < staticAlgorithms.size(); ++i )
			descriptions[ i ] = staticAlgorithms.get( i ).getDescription();
		
		if ( defaultAlgorithm >= descriptions.length )
			defaultAlgorithm = 0;

		// which timepoints are part of the 
		final List< TimePoint > timepointToProcess = SpimData2.getAllTimePointsSorted( data, viewIds );

		// ask which channels have the objects we are searching for
		final List< Channel > channels = SpimData2.getAllChannelsSorted( data, viewIds );
		final int nAllChannels = data.getSequenceDescription().getAllChannelsOrdered().size();

		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Basic Registration Parameters" );

		gd.addChoice( "Registration_algorithm", descriptions, descriptions[ defaultAlgorithm ] );

		final String[] choicesGlobal;
		if ( timepointToProcess.size() > 1 )
			choicesGlobal = registrationTypes.clone();
		else
		{
			final int globalAmountTimepoints = data.getSequenceDescription().getTimePoints().size();

			// suggest a registration to a reference timepoint (that we do not process here)
			// if there the entire dataset description has more than one timepoint
			if ( globalAmountTimepoints > 1 )
				choicesGlobal = new String[]{ registrationTypes[ 0 ], registrationTypes[ 1 ] };
			else
				choicesGlobal = new String[]{ registrationTypes[ 0 ] };
		}

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
			return false;
		
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
				return false;
		}

		// assemble which channels have been selected with with label
		final ArrayList< ChannelProcess > channelsToProcess = new ArrayList< ChannelProcess >();
		i = 0;
		
		for ( final Channel channel : channels )
		{
			final int channelChoice = defaultChannelLabels[ channel.getId() ] = gd.getNextChoiceIndex();
			
			if ( channelChoice < channelLabels.get( i ).length - 1 )
			{
				String label = channelLabels.get( i )[ channelChoice ];
				
				if ( label.contains( warningLabel ) )
					label = label.substring( 0, label.indexOf( warningLabel ) );
				
				channelsToProcess.add( new ChannelProcess( channel, label ) );
			}
			++i;
		}
		
		if ( channelsToProcess.size() == 0 )
		{
			IOFunctions.println( "No channels selected. Quitting." );
			return false;
		}

		for ( final ChannelProcess c : channelsToProcess )
			IOFunctions.println( "registering channel: " + c.getChannel().getId()  + " label: '" + c.getLabel() + "'" );
		
		final InterestPointRegistration ipr = staticAlgorithms.get( algorithm ).newInstance( data, viewIds, channelsToProcess );

		IOFunctions.println( "Registration algorithm: " + ipr.getDescription() );
		IOFunctions.println( "Registration type: " + registrationType.name() );
		IOFunctions.println( "Channels to process: " + channelsToProcess.size() );

		final GenericDialog gd2 = new GenericDialog( "Register: " + registrationTypes[ registrationType.ordinal() ] );
		
		if ( registrationType == RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			// assemble all timepoints, each one could be a reference
			final String[] tpList = assembleTimepoints( data.getSequenceDescription().getTimePoints() );
			
			// by default, the reference timepoint is the first one
			if ( defaultReferenceTimepointIndex < 0 || defaultReferenceTimepointIndex >= tpList.length )
				defaultReferenceTimepointIndex = 0;

			gd2.addChoice( "Reference timepoint", tpList, tpList[ defaultReferenceTimepointIndex ] );
			gd2.addMessage( "" );
		}
		else if ( registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
		{
			gd2.addSlider( "Range for all-to-all timepoint matching", 2, 10, defaultRange );
		}

		// for all registrations that include multiple timepointss
		if ( registrationType != RegistrationType.TIMEPOINTS_INDIVIDUALLY )
		{
			gd2.addCheckbox( "Consider_each_timepoint_as_rigid_unit", defaultConsiderTimepointAsUnit );
			gd2.addMessage( "Note: This option applies the same transformation model to all views of one timepoint. This makes for example\n" +
					"sense if all timepoints are individually pre-registered using an affine transformation model, and for the timeseries\n" +
					"stabilization a translation model should be used.\n ", GUIHelper.smallStatusFont );
		}

		if ( registrationType != RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			gd2.addChoice( "Fix_tiles", fixTilesChoice, fixTilesChoice[ defaultFixTiles ] );
			gd2.addChoice( "Map_back_tiles", mapBackChoice, mapBackChoice[ defaultMapBack ] );
		}

		gd2.addMessage( "" );
		gd2.addMessage( "Algorithm parameters [" + ipr.getDescription() + "]", new Font( Font.SANS_SERIF, Font.BOLD, 12 ) );
		gd2.addMessage( "" );

		ipr.addQuery( gd2, registrationType );

		if ( timepointToProcess.size() > 1 )
			gd2.addCheckbox( "Show_timeseries_statistics", defaultShowStatistics );

		// display the dialog
		gd2.showDialog();

		if ( gd2.wasCanceled() )
			return false;

		int referenceTimePoint = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 ).getId();
		int range = defaultRange;
		
		if ( registrationType == RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			referenceTimePoint = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( defaultReferenceTimepointIndex = gd2.getNextChoiceIndex() ).getId();

			// check that at least one of the views of the reference timepoint is part of the viewdescriptions
			boolean contains = false;

			for ( final ViewId viewId : viewIds )
				if ( viewId.getTimePointId() == referenceTimePoint )
					contains = true;

			if ( !contains )
			{
				IOFunctions.println( "No views of the reference timepoint are part of the registration." );
				IOFunctions.println( "Please re-run and select the corresponding views that should be used as reference." );

				return false;
			}
		}

		if ( registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
			range = defaultRange = (int)Math.round( gd2.getNextNumber() );

		final boolean considerTimepointsAsUnit;
		if ( registrationType != RegistrationType.TIMEPOINTS_INDIVIDUALLY )
			considerTimepointsAsUnit = defaultConsiderTimepointAsUnit = gd2.getNextBoolean();
		else
			considerTimepointsAsUnit = false;

		final int fixTiles, mapBack;

		if ( registrationType != RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			fixTiles = defaultFixTiles = gd2.getNextChoiceIndex();
			mapBack = defaultMapBack = gd2.getNextChoiceIndex();
		}
		else
		{
			fixTiles = mapBack = -1;
		}

		if ( !ipr.parseDialog( gd2, registrationType ) )
			return false;

		final boolean showStatistics;
		if ( timepointToProcess.size() > 1 )
			defaultShowStatistics = showStatistics = gd2.getNextBoolean();
		else
			showStatistics = false;

		// perform the actual registration(s)
		final GlobalOptimizationType type;
		
		if ( registrationType == RegistrationType.TIMEPOINTS_INDIVIDUALLY )
			type = new IndividualTimepointRegistration( data, viewIds, channelsToProcess );
		else if ( registrationType == RegistrationType.TO_REFERENCE_TIMEPOINT )
			type = new ReferenceTimepointRegistration( data, viewIds, channelsToProcess, data.getSequenceDescription().getTimePoints().getTimePoints().get( referenceTimePoint ), considerTimepointsAsUnit );
		else if ( registrationType == RegistrationType.ALL_TO_ALL )
			type = new AllToAllRegistration( data, viewIds, channelsToProcess, considerTimepointsAsUnit );
		else if ( registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
			type = new AllToAllRegistrationWithRange( data, viewIds, channelsToProcess, range, considerTimepointsAsUnit );
		else
			type = null;

		// set the fixed tiles and the potential mapping back to some tile
		if ( !setFixedTilesAndReference( fixTiles, mapBack, type ) )
			return false;

		if ( !ipr.register( type, saveXML, showStatistics ) )
			return false;

		// save the XML including transforms and correspondences
		if ( saveXML )
			SpimData2.saveXML( data, xmlFileName, clusterExtension );

		if ( showStatistics )
		{
			final ArrayList< RegistrationStatistics > rsData = new ArrayList< RegistrationStatistics >();
			for ( final TimePoint t : timepointToProcess )
				rsData.add( new RegistrationStatistics( t.getId(), ipr.getStatistics() ) );
			TimeLapseDisplay.plotData( data.getSequenceDescription().getTimePoints(), rsData, TimeLapseDisplay.getOptimalTimePoint( rsData ), true );
		}

		return true;
	}

	/**
	 * Assign the right fixed tiles and reference tiles for this type of optimization
	 * 
	 * @param fixTilesIndex - "Fix first tile", "Select fixed tile", "Do not fix tiles"
	 * @param mapBackIndex - "Do not map back (use this if tiles are fixed)", "Map back to first tile using translation model", "Map back to first tile using rigid model", "Map back to user defined tile using translation model", "Map back to user defined tile using rigid model"
	 */
	public boolean setFixedTilesAndReference( final int fixTilesIndex, final int mapBackIndex, final GlobalOptimizationType type )
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

	protected boolean askForReferenceTile( final GlobalOptimizationSubset subset, final GlobalOptimizationType type, final String title )
	{
		final GenericDialog gd = new GenericDialog( title );

		final String[] choice = new String[ subset.getViews().size() ];

		for ( int i = 0; i < choice.length; ++i )
		{
			final ViewSetup vs = type.getSpimData().getSequenceDescription().getViewDescription( subset.getViews().get( i ) ).getViewSetup();
			choice[ i ] = "Angle:" + vs.getAngle().getName() + " Channel:" + vs.getChannel().getName() + " Illum:" + vs.getIllumination().getName() + " Timepoint:" + subset.getViews().get( i ).getTimePointId();
		}

		if ( defaultReferenceTile >= choice.length )
			defaultReferenceTile = 0;

		gd.addChoice( title.replace( " ", "_" ), choice, choice[ defaultReferenceTile ] );
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;

		type.setMapBackReferenceTile( subset, subset.getViews().get( defaultReferenceTile = gd.getNextChoiceIndex() ) );

		return true;
	}

	protected boolean askForFixedTiles( final GlobalOptimizationSubset subset, final GlobalOptimizationType type, final Set< ViewId > fixedTiles, final String title )
	{
		final GenericDialog gd = new GenericDialog( title );

		if ( defaultFixedTiles == null || defaultFixedTiles.length != subset.getViews().size() )
			defaultFixedTiles = new boolean[ subset.getViews().size() ];

		for ( int i = 0; i < subset.getViews().size(); ++i )
		{
			final ViewId viewId = subset.getViews().get( i );
			final ViewSetup vs = type.getSpimData().getSequenceDescription().getViewDescription( viewId ).getViewSetup();
			gd.addCheckbox( "Angle_" + vs.getAngle().getName() + "_Channel_" + vs.getChannel().getName() + "_Illum_" + vs.getIllumination().getName() + "_Timepoint_" + viewId.getTimePointId(), defaultFixedTiles[ i ] );
		}
		
		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		for ( int i = 0; i < subset.getViews().size(); ++i )
			if ( defaultFixedTiles[ i ] = gd.getNextBoolean() )
				fixedTiles.add( subset.getViews().get( i ) );
		
		return true;
	}

	protected ArrayList< ViewSetup > getListOfViewSetupPresentInAllSubsets( final List< GlobalOptimizationSubset > subsets, final GlobalOptimizationType type )
	{
		final HashMap< Integer, Integer > viewsetups = new HashMap< Integer, Integer >();
		
		for ( final GlobalOptimizationSubset subset : subsets )
			for ( final ViewId viewId : subset.getViews() )
			{
				if ( viewsetups.containsKey( viewId.getViewSetupId() ) )
					viewsetups.put( viewId.getViewSetupId(), viewsetups.get( viewId.getViewSetupId() ) + 1 );
				else
					viewsetups.put( viewId.getViewSetupId(), 1 );
			}

		final ArrayList< ViewSetup > setupList = new ArrayList< ViewSetup >();

		for ( final int viewSetupId : viewsetups.keySet() )
			if ( viewsetups.get( viewSetupId ) == subsets.size() )
				setupList.add( type.getSpimData().getSequenceDescription().getViewSetups().get( viewSetupId ) );

		Collections.sort( setupList );

		return setupList;
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

	protected static String[] assembleTimepoints( final TimePoints timepoints )
	{
		final String[] tps = new String[ timepoints.size() ];

		for ( int t = 0; t < tps.length; ++t )
			tps[ t ] = timepoints.getTimePointsOrdered().get( t ).getName();

		return tps;
	}

	public static void main( String[] args )
	{
		LoadParseQueryXML.defaultXMLfilename = "/Users/preibischs/Downloads/worm7bugtester/worm7.xml";
		new ImageJ();
		new Interest_Point_Registration().run( null );
	}
}
