package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.models.RigidModel3D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
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
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.geometricdescriptor.RGLDM;
import spim.process.interestpointregistration.geometrichashing.GeometricHashing;
import spim.process.interestpointregistration.icp.IterativeClosestPoint;
import spim.process.interestpointregistration.optimizationtypes.AllToAllRegistration;
import spim.process.interestpointregistration.optimizationtypes.AllToAllRegistrationWithRange;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;
import spim.process.interestpointregistration.optimizationtypes.IndividualTimepointRegistration;
import spim.process.interestpointregistration.optimizationtypes.ReferenceTimepointRegistration;

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
	public static boolean defaultDisplayTransformOnly = false;
	public static boolean defaultSameFixedViews = true;
	public static boolean defaultSameReferenceView = true;

	public static boolean[] defaultFixedTiles = null;
	public static int defaultReferenceTile = 0;

	public final static String warningLabel = " (WARNING: Only available for "; 
	
	static
	{
		IOFunctions.printIJLog = true;
		staticAlgorithms.add( new GeometricHashing( null, null, null, null, null ) );
		staticAlgorithms.add( new RGLDM( null, null, null, null, null ) );
		staticAlgorithms.add( new IterativeClosestPoint( null, null, null, null, null ) );
	}

	@Override
	public void run( final String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "for performing interest point registration", true, false, true, true ) )
			return;
		
		// the GenericDialog needs a list[] of String for the algorithms that can register
		final String[] descriptions = new String[ staticAlgorithms.size() ];
		
		for ( int i = 0; i < staticAlgorithms.size(); ++i )
			descriptions[ i ] = staticAlgorithms.get( i ).getDescription();
		
		if ( defaultAlgorithm >= descriptions.length )
			defaultAlgorithm = 0;

		// ask which channels have the objects we are searching for
		final List< Channel > channels = result.getData().getSequenceDescription().getAllChannelsOrdered();

		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Basic Registration Parameters" );

		gd.addChoice( "Registration_algorithm", descriptions, descriptions[ defaultAlgorithm ] );

		final String[] choicesGlobal;
		if ( result.getTimePointsToProcess().size() > 1 )
			choicesGlobal = registrationTypes.clone();
		else
		{
			final int globalAmountTimepoints =
					result.getData().getSequenceDescription().getTimePoints().size();

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
		
		if ( defaultChannelLabels == null || defaultChannelLabels.length != channels.size() )
			defaultChannelLabels = new int[ channels.size() ];
		
		// check which channels and labels are available and build the choices
		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int i = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = getAllInterestPointLabelsForChannel(
					result.getData(), result.getTimePointsToProcess(),
					result.getAnglesToProcess(),
					result.getIlluminationsToProcess(),
					channel,
					"register" );
			
			if ( channelLabels == null )
				return;
			
			if ( defaultChannelLabels[ channel.getId() ] >= labels.length )
				defaultChannelLabels[ channel.getId() ] = 0;
			
			gd.addChoice( "Interest_points_channel_" + channel.getName(), labels, labels[ defaultChannelLabels[ i++ ] ] );
			channelLabels.add( labels );
		}

		// assemble the last registration names of all viewsetups involved
		final HashMap< String, Integer > names = GUIHelper.assembleRegistrationNames( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );
		gd.addMessage( "" );
		GUIHelper.displayRegistrationNames( gd, names );
		gd.addMessage( "" );

		GUIHelper.addWebsite( gd );

		if ( names.keySet().size() > 5 )
			GUIHelper.addScrollBars( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
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
				return;
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
			return;
		}
		
		if (
				result.getAnglesToProcess().size() * result.getIlluminationsToProcess().size() * channelsToProcess.size() <= 1 && 
				registrationType == RegistrationType.TIMEPOINTS_INDIVIDUALLY )
		{
			IOFunctions.println( "You selected/have just one view setup per timepoint and to register timepoints individually. Nothing to do here. Quitting." );
			return;
		}
		
		for ( final ChannelProcess c : channelsToProcess )
			IOFunctions.println( "registering channel: " + c.getChannel().getId()  + " label: '" + c.getLabel() + "'" );
		
		final InterestPointRegistration ipr = staticAlgorithms.get( algorithm ).newInstance(
				result.getData(),
				result.getAnglesToProcess(),
				channelsToProcess,
				result.getIlluminationsToProcess(),
				result.getTimePointsToProcess());

		IOFunctions.println( "Registration algorithm: " + ipr.getDescription() );
		IOFunctions.println( "Registration type: " + registrationType.name() );
		IOFunctions.println( "Channels to process: " + channelsToProcess.size() );

		// call the next dialog that asks for specific details
		queryDetailedParameters( result, ipr, registrationType );
	}
	
	protected void queryDetailedParameters( final LoadParseQueryXML result, final InterestPointRegistration ipr, final RegistrationType registrationType )
	{
		final GenericDialog gd = new GenericDialog( "Register: " + registrationTypes[ registrationType.ordinal() ] );
		
		if ( registrationType == RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			final String[] tpList = assembleTimepoints( result.getData().getSequenceDescription().getTimePoints() );
			
			// by default, the reference timepoint is the first one
			if ( defaultReferenceTimepointIndex < 0 || defaultReferenceTimepointIndex >= tpList.length )
				defaultReferenceTimepointIndex = 0;

			gd.addChoice( "Reference timepoint", tpList, tpList[ defaultReferenceTimepointIndex ] );
			gd.addMessage( "" );
		}
		else if ( registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
		{
			gd.addSlider( "Range for all-to-all timepoint matching", 2, 10, defaultRange );
		}

		// for all registrations that include multiple timepointss
		if ( registrationType != RegistrationType.TIMEPOINTS_INDIVIDUALLY )
		{
			gd.addCheckbox( "Consider_each_timepoint_as_rigid_unit", defaultConsiderTimepointAsUnit );
			gd.addMessage( "Note: This option applies the same transformation model to all views of one timepoint. This makes for example\n" +
					"sense if all timepoints are individually pre-registered using an affine transformation model, and for the timeseries\n" +
					"stabilization a translation model should be used.\n ", GUIHelper.smallStatusFont );
		}

		gd.addCheckbox( "Display final transformation only (do not edit XML)", defaultDisplayTransformOnly );

		if ( registrationType != RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			gd.addChoice( "Fix_tiles", fixTilesChoice, fixTilesChoice[ defaultFixTiles ] );
			gd.addChoice( "Map_back_tiles", mapBackChoice, mapBackChoice[ defaultMapBack ] );
		}

		gd.addMessage( "" );
		gd.addMessage( "Algorithm parameters [" + ipr.getDescription() + "]", new Font( Font.SANS_SERIF, Font.BOLD, 12 ) );
		gd.addMessage( "" );
		
		ipr.addQuery( gd, registrationType );
		
		// display the dialog
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;

		int referenceTimePoint = 0;result.getData().getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 ).getId();
		int range = defaultRange;
		
		if ( registrationType == RegistrationType.TO_REFERENCE_TIMEPOINT )
			referenceTimePoint = result.getData().getSequenceDescription().getTimePoints().getTimePointsOrdered().get( defaultReferenceTimepointIndex = gd.getNextChoiceIndex() ).getId();

		if ( registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
			range = defaultRange = (int)Math.round( gd.getNextNumber() );

		final boolean considerTimepointsAsUnit;
		if ( registrationType != RegistrationType.TIMEPOINTS_INDIVIDUALLY )
			considerTimepointsAsUnit = defaultConsiderTimepointAsUnit = gd.getNextBoolean();
		else
			considerTimepointsAsUnit = false;
		final boolean displayOnly = defaultDisplayTransformOnly = gd.getNextBoolean();
		
		final int fixTiles, mapBack;
		
		if ( registrationType != RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			fixTiles = defaultFixTiles = gd.getNextChoiceIndex();
			mapBack = defaultMapBack = gd.getNextChoiceIndex();
		}
		else
		{
			fixTiles = mapBack = -1;
		}

		if ( !ipr.parseDialog( gd, registrationType ) )
			return;

		// perform the actual registration(s)
		final GlobalOptimizationType type;
		
		if ( registrationType == RegistrationType.TIMEPOINTS_INDIVIDUALLY )
		{
			type = new IndividualTimepointRegistration(
					result.getData(),
					ipr.getAnglesToProcess(),
					ipr.getChannelsToProcess(),
					ipr.getIllumsToProcess(),
					ipr.getTimepointsToProcess(),
					!displayOnly );
		}
		else if ( registrationType == RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			type = new ReferenceTimepointRegistration(
					result.getData(),
					ipr.getAnglesToProcess(),
					ipr.getChannelsToProcess(),
					ipr.getIllumsToProcess(),
					ipr.getTimepointsToProcess(),
					result.getData().getSequenceDescription().getTimePoints().getTimePoints().get( referenceTimePoint ),
					!displayOnly,
					considerTimepointsAsUnit );
		}
		else if ( registrationType == RegistrationType.ALL_TO_ALL )
		{
			type = new AllToAllRegistration(
					result.getData(),
					ipr.getAnglesToProcess(),
					ipr.getChannelsToProcess(),
					ipr.getIllumsToProcess(),
					ipr.getTimepointsToProcess(),
					!displayOnly,
					considerTimepointsAsUnit );
		}
		else if ( registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
		{
			type = new AllToAllRegistrationWithRange(
					result.getData(),
					ipr.getAnglesToProcess(),
					ipr.getChannelsToProcess(),
					ipr.getIllumsToProcess(),
					ipr.getTimepointsToProcess(),
					range,
					!displayOnly,
					considerTimepointsAsUnit );
		}
		else
		{
			type = null;
		}

		// set the fixed tiles and the potential mapping back to some tile
		if ( !setFixedTilesAndReference( fixTiles, mapBack, type ) )
			return;

		if ( !ipr.register( type ) )
			return;

		// save the XML including transforms and correspondences
		if ( !displayOnly )
			saveXML( result.getData(), result.getXMLFileName() );
	}
	
	public static void saveXML( final SpimData2 data, final String xmlFileName  )
	{
		// save the xml
		final XmlIoSpimData2 io = new XmlIoSpimData2();
		
		final String xml = new File( data.getBasePath(), new File( xmlFileName ).getName() ).getAbsolutePath();
		try 
		{
			io.save( data, xml );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + xml + "'." );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + xml + "': " + e );
			e.printStackTrace();
		}
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
				fixedTiles.add( subset.getViews().get( 0 ) );
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
			// no fixed tiles
		}

		type.setFixedTiles( fixedTiles );

		IOFunctions.println( "Following tiles are fixed:" );
		for ( final ViewId id : fixedTiles )
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

		IOFunctions.println( "Following tiles are reference tiles:" );
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
	 * @param timepointsToProcess
	 * @param channel
	 * @return
	 */
	public static String[] getAllInterestPointLabelsForChannel(
			final SpimData2 spimData,
			final List< TimePoint > timepointsToProcess,
			final List< Angle > anglesToProcess,
			final List< Illumination > illuminationsToProcess,
			final Channel channel )
	{
		return getAllInterestPointLabelsForChannel( spimData, timepointsToProcess, anglesToProcess, illuminationsToProcess, channel, null );
	}

	/**
	 * Goes through all ViewDescriptions and checks all available labels for interest point detection
	 * 
	 * @param spimData
	 * @param timepointsToProcess
	 * @param channel
	 * @param doWhat - the text for not doing anything with this channel
	 * @return
	 */
	public static String[] getAllInterestPointLabelsForChannel(
			final SpimData2 spimData,
			final List< TimePoint > timepointsToProcess,
			final List< Angle > anglesToProcess,
			final List< Illumination > illuminationsToProcess,
			final Channel channel,
			final String doWhat )
	{
		final ViewInterestPoints interestPoints = spimData.getViewInterestPoints();
		final HashMap< String, Integer > labels = new HashMap< String, Integer >();
		
		int countViewDescriptions = 0;

		for ( final TimePoint t : timepointsToProcess )
			for ( final Angle a : anglesToProcess )
				for ( final Illumination i : illuminationsToProcess )
				{
					final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, channel, a, i );
					
					// this happens only if a viewsetup is not present in any timepoint
					// (e.g. after appending fusion to a dataset)
					if ( viewId == null )
						continue;
					
					// get the viewdescription
					final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
							viewId.getTimePointId(), viewId.getViewSetupId() );

					// check if the view is present
					if ( !viewDescription.isPresent() )
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
			allLabels[ i ] = "[DO NOT " + doWhat + " this channel]";

		return allLabels;
	}

	protected String[] assembleTimepoints( final TimePoints timepoints )
	{
		final String[] tps = new String[ timepoints.size() ];

		for ( int t = 0; t < tps.length; ++t )
			tps[ t ] = timepoints.getTimePointsOrdered().get( t ).getName();

		return tps;
	}

	public static void main( String[] args )
	{
		new ImageJ();
		new Interest_Point_Registration().run( null );
	}
}
