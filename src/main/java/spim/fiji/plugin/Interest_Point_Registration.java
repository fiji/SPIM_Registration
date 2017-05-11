package spim.fiji.plugin;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
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
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.interestpointregistration.pairwise.CenterOfMassGUI;
import spim.fiji.plugin.interestpointregistration.pairwise.GeometricHashingGUI;
import spim.fiji.plugin.interestpointregistration.pairwise.IterativeClosestPointGUI;
import spim.fiji.plugin.interestpointregistration.pairwise.PairwiseGUI;
import spim.fiji.plugin.interestpointregistration.pairwise.RGLDMGUI;
import spim.fiji.plugin.interestpointregistration.parameters.AdvancedRegistrationParameters;
import spim.fiji.plugin.interestpointregistration.parameters.BasicRegistrationParameters;
import spim.fiji.plugin.interestpointregistration.parameters.FixMapBackParameters;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.interestpointregistration.TransformationTools;
import spim.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import spim.process.interestpointregistration.pairwise.constellation.Subset;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;

/**
*
* @author Stephan Preibisch (stephan.preibisch@gmx.de)
*
*/
public class Interest_Point_Registration implements PlugIn
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
		"Do not fix views" };

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
	public static int defaultLabel = 0;

	// advanced dialog
	public static int defaultRange = 5;
	public static int defaultReferenceTimepointIndex = -1;
	public static boolean defaultConsiderTimepointAsUnit = false;
	public static int defaultFixViews = 0;
	public static int defaultMapBack = 0;
	public static boolean defaultShowStatistics = true;

	// fix and map back dialog
	public static boolean defaultSameFixedViews = true;
	public static boolean defaultSameReferenceView = true;
	public static boolean[] defaultFixedViews = null;
	public static int defaultReferenceView = 0;

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

		// query basic registration parameters
		final BasicRegistrationParameters brp = basicRegistrationParameters( timepointToProcess, nAllTimepoints, data, viewIds );

		if ( brp == null )
			return false;

		// query advanced parameters
		final AdvancedRegistrationParameters arp = advancedRegistrationParameters( brp, timepointToProcess, data, viewIds );

		if ( arp == null )
			return false;

		// identify subsets
		final Set< Group< ViewId > > groups = arp.getGroups( viewIds );
		final PairwiseSetup< ViewId > setup = arp.pairwiseSetupInstance( brp.registrationType, viewIds, groups );

		System.out.println( "Defined pairs, removed " + setup.definePairs().size() + " redundant view pairs." );
		System.out.println( "Removed " + setup.removeNonOverlappingPairs( new SimpleBoundingBoxOverlap<>( data ) ).size() + " pairs because they do not overlap." );
		setup.reorderPairs();
		setup.detectSubsets();
		setup.sortSubsets();
		final ArrayList< Subset< ViewId > > subsets = setup.getSubsets();
		System.out.println( "Identified " + subsets.size() + " subsets " );

		final FixMapBackParameters mpbp = fixMapBackParameters( setup, subsets, arp.fixViewsIndex, arp.mapBackIndex, brp.registrationType );

		if ( mpbp == null )
			return false;

		return true;
	}

	public boolean processRegistration(
			final BasicRegistrationParameters brp,
			final AdvancedRegistrationParameters arp,
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		// load & transform all interest points
		final Map< ViewId, List< InterestPoint > > interestpoints =
				TransformationTools.getAllTransformedInterestPoints(
					viewIds,
					data.getViewRegistrations().getViewRegistrations(),
					data.getViewInterestPoints().getViewInterestPoints(),
					brp.labelMap );

		// define fixed views
		final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
		fixedViews.add( viewIds.get( 0 ) );

		// define groups
		//final ArrayList< ArrayList< ViewId > > groupedViews = new ArrayList< ArrayList< ViewId > >();

		// define all pairs depending on the registrationtype
		//final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.allToAll( viewIds, fixedViews, groupedViews );

		// set the fixed views and the potential mapping back to some view
		//if ( !setFixedViewsAndReference( fixViews, mapBack, brp.registrationType ) )
		//	return false;

		return true;
	}

	public AdvancedRegistrationParameters advancedRegistrationParameters(
			final BasicRegistrationParameters brp,
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

		// whenever it is not a registration to a reference timepoint choose potentially fixed views
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

		final AdvancedRegistrationParameters arp = new AdvancedRegistrationParameters();

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

	public BasicRegistrationParameters basicRegistrationParameters(
			final List< TimePoint > timepointToProcess,
			final int nAllTimepoints,
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

		// check which channels and labels are available and build the choices
		final String[] labels = getAllInterestPointLabels( data, viewIds );

		if ( labels.length == 0 )
		{
			IOFunctions.printErr( "No interest points available, stopping. Please run Interest Ppint Detection first" );
			return null;
		}

		if ( defaultLabel >= labels.length )
			defaultLabel = 0;

		gd.addChoice( "Interest_points" , labels, labels[ defaultLabel ] );

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
		final int choice = defaultLabel = gd.getNextChoiceIndex();

		String label = labels[ choice ];

		if ( label.contains( warningLabel ) )
			label = label.substring( 0, label.indexOf( warningLabel ) );

		final PairwiseGUI pwr = staticPairwiseAlgorithms.get( algorithm ).newInstance();

		IOFunctions.println( "Registration algorithm: " + pwr.getDescription() );
		IOFunctions.println( "Registration type: " + registrationType.name() );

		final BasicRegistrationParameters brp = new BasicRegistrationParameters();
		brp.pwr = pwr;
		brp.registrationType = registrationType;
		brp.labelMap = new HashMap<>();

		for ( final ViewId viewId : viewIds )
			brp.labelMap.put( viewId, label );

		return brp;
	}

	/**
	 * Assign the right fixed views and reference views for this type of optimization
	 *
	 * @param subsets - which subsets exist
	 * @param fixViewsIndex - "Fix first views", "Select fixed view", "Do not fix views"
	 * @param mapBackIndex - 
	 * 				"Do not map back (use this if views are fixed)",
	 * 				"Map back to first view using translation model",
	 * 				"Map back to first view using rigid model",
	 * 				"Map back to user defined view using translation model",
	 * 				"Map back to user defined view using rigid model"
	 * @param type - the type of registration used
	 */
	public FixMapBackParameters fixMapBackParameters(
			final PairwiseSetup< ViewId > setup,
			final List< Subset< ViewId > > subsets,
			final int fixViewsIndex,
			final int mapBackIndex,
			final RegistrationType type )
	{
		final FixMapBackParameters fmbp = new FixMapBackParameters();

		//
		// define fixed views
		//
		fmbp.fixedViews = new HashSet< ViewId >();

		if ( type == RegistrationType.TO_REFERENCE_TIMEPOINT )
		{
			fmbp.fixedViews.addAll( setup.getDefaultFixedViews() );
			fmbp.model = null;
			fmbp.mapBackView = new HashMap< Subset< ViewId >, ViewId >();

			return fmbp;
		}

		if ( fixViewsIndex == 0 ) // all first views
		{
			for ( final Subset< ViewId > subset : subsets )
			{
				if ( subset.getViews().size() == 0 )
					IOFunctions.println( "Nothing to do for: " + subset + ". No views fixed." );
				else
					fmbp.fixedViews.add( Subset.getViewsSorted( subset.getViews() ).get( 0 ) );
			}
		}
		else if ( fixViewsIndex == 1 )
		{
			// TODO: select fixed views (this assumes that one subset consists of one timepoint)
			if ( subsets.size() > 1 )
			{
				final GenericDialog gd1 = new GenericDialog( "Type of manual choice" );
				gd1.addCheckbox( "Same_fixed_view(s) for each timepoint", defaultSameFixedViews );

				gd1.showDialog();
				if ( gd1.wasCanceled() )
					return null;

				if ( defaultSameFixedViews = gd1.getNextBoolean() )
				{
					// check which viewsetups are available in all subsets
					final ArrayList< Integer > setupList = getListOfViewSetupIdsPresentInAllSubsets( subsets );

					if ( setupList.size() == 0 )
					{
						IOFunctions.println( "No Viewsetup is available in all Timepoints." );
						return null;
					}

					if ( defaultFixedViews == null || defaultFixedViews.length != setupList.size() )
						defaultFixedViews = new boolean[ setupList.size() ];

					final GenericDialog gd2 = new GenericDialog( "Select ViewSetups to be fixed for each of the timepoints" );

					for ( int i = 0; i < setupList.size(); ++i )
						gd2.addCheckbox( "ViewSetupId_" + setupList.get( i ), defaultFixedViews[ i ] );

					GUIHelper.addScrollBars( gd2 );

					gd2.showDialog();
					if ( gd2.wasCanceled() )
						return null;

					for ( int i = 0; i < setupList.size(); ++i )
						if ( defaultFixedViews[ i ] = gd2.getNextBoolean() )
							for ( final Subset< ViewId > subset : subsets )
								fmbp.fixedViews.add( new ViewId( subset.getViews().iterator().next().getTimePointId(), setupList.get( i ) ) );
				}
				else
				{
					for ( final Subset< ViewId > subset : subsets )
						if ( !askForFixedViews( subset, fmbp.fixedViews, "Select fixed ViewIds for timepoint " + subset.getViews().iterator().next().getTimePointId() ) )
							return null;
				}
			}
			else
			{
				// there is just one subset
				if ( !askForFixedViews( subsets.get( 0 ), fmbp.fixedViews, "Select fixed ViewIds" ) )
					return null;
			}
		}
		else
		{
			// no fixed views or reference timepoint
		}

		IOFunctions.println( "Following views are fixed:" );
		for ( final ViewId id : fmbp.fixedViews )
			IOFunctions.println( "ViewSetupId:" + id.getViewSetupId() + " TimePoint:" + id.getTimePointId() );

		//
		// now the reference view(s)
		//
		if ( mapBackIndex == 0 )
		{
			fmbp.model = null;
			fmbp.mapBackView = new HashMap< Subset< ViewId >, ViewId >();
		}
		else if ( mapBackIndex == 1 || mapBackIndex == 3 )
		{
			fmbp.model = new TranslationModel3D();
		}
		else
		{
			fmbp.model = new RigidModel3D();
		}

		if ( mapBackIndex == 1 || mapBackIndex == 2 )
		{
			for ( final Subset< ViewId > subset : subsets )
				fmbp.mapBackView.put( subset, Subset.getViewsSorted( subset.getViews() ).get( 0 ) );
		}
		else if ( mapBackIndex == 3 || mapBackIndex == 4 )
		{
			// select reference view
			// select fixed views (this assumes that one subset consists of one timepoint)
			if ( subsets.size() > 1 )
			{
				final GenericDialog gd1 = new GenericDialog( "Type of manual choice" );
				gd1.addCheckbox( "Same_reference_view(s) for each timepoint", defaultSameReferenceView );

				gd1.showDialog();
				if ( gd1.wasCanceled() )
					return null;

				if ( defaultSameReferenceView = gd1.getNextBoolean() )
				{
					// check which viewsetups are available in all subsets
					final ArrayList< Integer > setupList = getListOfViewSetupIdsPresentInAllSubsets( subsets );

					if ( setupList.size() == 0 )
					{
						IOFunctions.println( "No Viewsetup is available in all Timepoints." );
						return null;
					}

					final GenericDialog gd2 = new GenericDialog( "Select Reference ViewSetup each of the timepoints" );

					final String[] choices = new String[ setupList.size() ];
					
					for ( int i = 0; i < setupList.size(); ++ i )
						choices[ i ] = "ViewSetupId_" + setupList.get( i );

					if ( defaultReferenceView >= choices.length )
						defaultReferenceView = 0;

					gd2.addChoice( "Select_Reference_ViewSetup", choices, choices[ defaultReferenceView ] );

					gd2.showDialog();
					if ( gd2.wasCanceled() )
						return null;

					final int index = defaultReferenceView = gd2.getNextChoiceIndex();

					for ( final Subset< ViewId > subset : subsets )
						fmbp.mapBackView.put( subset, new ViewId( subset.getViews().iterator().next().getTimePointId(), setupList.get( index ) ) );
				}
				else
				{
					for ( final Subset< ViewId > subset : subsets )
					{
						final ViewId ref = askForReferenceView( subset, "Select Reference Views" );

						if ( ref == null )
							return null;
						else
							fmbp.mapBackView.put( subset, ref );
					}
				}
			}
			else
			{
				final ViewId ref = askForReferenceView( subsets.get( 0 ), "Select Reference Views" );
				
				if ( ref == null )
					return null;
				else
					fmbp.mapBackView.put( subsets.get( 0 ), ref );
			}
		}
		else
		{
			// no reference view
		}

		IOFunctions.println( "Following views are references (for mapping back if there are no fixed views):" );
		for ( final Subset< ViewId > subset : subsets )
		{
			final ViewId id = fmbp.mapBackView.get( subset );
			if ( id != null )
				IOFunctions.println( "ViewSetupId: " + id.getViewSetupId() + " TimePoint:" + id.getTimePointId() );
		}

		return fmbp;
	}

	protected ArrayList< Integer > getListOfViewSetupIdsPresentInAllSubsets( final List< Subset< ViewId > > subsets )
	{
		final HashMap< Integer, Integer > viewsetups = new HashMap< Integer, Integer >();
		
		for ( final Subset< ViewId > subset : subsets )
			for ( final ViewId viewId : subset.getViews() )
			{
				if ( viewsetups.containsKey( viewId.getViewSetupId() ) )
					viewsetups.put( viewId.getViewSetupId(), viewsetups.get( viewId.getViewSetupId() ) + 1 );
				else
					viewsetups.put( viewId.getViewSetupId(), 1 );
			}

		final ArrayList< Integer > setupList = new ArrayList<>();

		for ( final int viewSetupId : viewsetups.keySet() )
			if ( viewsetups.get( viewSetupId ) == subsets.size() )
				setupList.add( viewSetupId );

		Collections.sort( setupList );

		return setupList;
	}

	protected boolean askForFixedViews( final Subset< ViewId > subset, final Set< ViewId > fixedViews, final String title )
	{
		final GenericDialog gd = new GenericDialog( title );

		final List< ViewId > views = Subset.getViewsSorted( subset.getViews() );

		if ( defaultFixedViews == null || defaultFixedViews.length != subset.getViews().size() )
			defaultFixedViews = new boolean[ subset.getViews().size() ];

		for ( int i = 0; i < subset.getViews().size(); ++i )
		{
			final ViewId viewId = views.get( i );
			gd.addCheckbox( "ViewSetupId_" + viewId.getViewSetupId() + "_Timepoint_" + viewId.getTimePointId(), defaultFixedViews[ i ] );
		}
		
		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		for ( int i = 0; i < subset.getViews().size(); ++i )
			if ( defaultFixedViews[ i ] = gd.getNextBoolean() )
				fixedViews.add( views.get( i ) );
		
		return true;
	}

	protected ViewId askForReferenceView( final Subset< ViewId > subset, final String title )
	{
		final GenericDialog gd = new GenericDialog( title );

		final List< ViewId > views = Subset.getViewsSorted( subset.getViews() );

		final String[] choice = new String[ subset.getViews().size() ];

		for ( int i = 0; i < choice.length; ++i )
			choice[ i ] = "ViewSetupId:" + views.get( i ).getViewSetupId() + " Timepoint:" + views.get( i ).getTimePointId();

		if ( defaultReferenceView >= choice.length )
			defaultReferenceView = 0;

		gd.addChoice( title.replace( " ", "_" ), choice, choice[ defaultReferenceView ] );
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		return views.get( defaultReferenceView = gd.getNextChoiceIndex() );
	}

	protected static String[] assembleTimepoints( final TimePoints timepoints )
	{
		final String[] tps = new String[ timepoints.size() ];

		for ( int t = 0; t < tps.length; ++t )
			tps[ t ] = timepoints.getTimePointsOrdered().get( t ).getName();

		return tps;
	}

	/**
	 * Goes through all Views and checks all available labels for interest point detection
	 * 
	 * @param spimData
	 * @param doWhat - the text for not doing anything with this channel
	 * @return
	 */
	public static String[] getAllInterestPointLabels(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess )
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

		final String[] allLabels = new String[ labels.keySet().size() ];

		int i = 0;
		
		for ( final String label : labels.keySet() )
		{
			allLabels[ i ] = label;

			if ( labels.get( label ) != countViewDescriptions )
				allLabels[ i ] += warningLabel + labels.get( label ) + "/" + countViewDescriptions + " Views!)";

			++i;
		}

		return allLabels;
	}

	/*
	 * Registers all timepoints. No matter which matching is done it is always the same principle.
	 * 
	 * First all pairwise correspondences are established, and then a global optimization is computed.
	 * The global optimization can is done in subsets, where the number of subsets &gt;= 1.
	 * 
	 * @param registrationType - which kind of registration
	 * @param save - if you want to save the correspondence files
	 * @return
	public boolean register( final GlobalOptimizationType registrationType, final boolean save, final boolean collectStatistics )
	{
		final SpimData2 spimData = getSpimData();

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Starting registration" );

		if ( collectStatistics )
			this.statistics = new ArrayList<>();

		// get a list of all pairs for this specific GlobalOptimizationType
		final List< GlobalOptimizationSubset > list = registrationType.getAllViewPairs();

		int successfulRuns = 0;

		for ( final GlobalOptimizationSubset subset : list )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Finding correspondences for subset: " + subset.getDescription() );

			final List< PairwiseMatch > pairs = subset.getViewPairs();

			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
			final ArrayList< Callable< PairwiseMatch > > tasks = new ArrayList< Callable< PairwiseMatch > >(); // your tasks

			for ( final PairwiseMatch pair : pairs )
			{
				// just for logging the names and results of pairwise comparison
				final ViewDescription viewA = spimData.getSequenceDescription().getViewDescription( pair.getViewIdA() );
				final ViewDescription viewB = spimData.getSequenceDescription().getViewDescription( pair.getViewIdB() );

				final String description = "[TP=" + viewA.getTimePoint().getName() + 
						" angle=" + viewA.getViewSetup().getAngle().getName() + ", ch=" + viewA.getViewSetup().getChannel().getName() +
						", illum=" + viewA.getViewSetup().getIllumination().getName() + " >>> TP=" + viewB.getTimePoint().getName() +
						" angle=" + viewB.getViewSetup().getAngle().getName() + ", ch=" + viewB.getViewSetup().getChannel().getName() +
						", illum=" + viewB.getViewSetup().getIllumination().getName() + "]";
				
				tasks.add( pairwiseMatchingInstance( pair, description ) );
			}
			try
			{
				// invokeAll() returns when all tasks are complete
				taskExecutor.invokeAll( tasks );
			}
			catch ( final InterruptedException e )
			{
				IOFunctions.println( "Failed to compute registrations for " + subset.getDescription() );
				e.printStackTrace();
			}
			
			
			// some statistics
			int sumCandidates = 0;
			int sumInliers = 0;
			for ( final PairwiseMatch pair : pairs )
			{
				sumCandidates += pair.getCandidates().size();
				sumInliers += pair.getInliers().size();
			}
			
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Number of Candidates: " + sumCandidates );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Number of Inliers: " + sumInliers );

			if ( collectStatistics )
				statistics.add( pairs );

			//
			// set and store correspondences
			//
			
			// first remove existing correspondences
			registrationType.clearExistingCorrespondences( subset );

			// now add all corresponding interest points
			registrationType.addCorrespondences( pairs );

			// save the files
			if ( save )
				registrationType.saveCorrespondences( subset );

			if ( runGlobalOpt( subset, registrationType ) )
				++successfulRuns;
		}
		
		if ( successfulRuns > 0 )
			return true;
		else
			return false;
	}
}
	 */
}
