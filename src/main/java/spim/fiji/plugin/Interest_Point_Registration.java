package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIo;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.geometricdescriptor.RGLDM;
import spim.process.interestpointregistration.geometrichashing3d.GeometricHashing3d;
import spim.process.interestpointregistration.optimizationtypes.AllToAllRegistration;
import spim.process.interestpointregistration.optimizationtypes.AllToAllRegistrationWithRange;
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
	public static String[] registrationChoices = { "Register timepoints individually", "Match against one reference timepoint (no global optimization)", "All-to-all timepoints matching (global optimization)", "All-to-all timepoints matching with range ('reasonable' global optimization)" };
	
	public static int defaultAlgorithm = 0;
	public static int defaultRegistration = 0;
	public static int[] defaultChannelLabels = null;
	public static int defaultRange = 5;
	public static int defaultReferenceTimepoint = -1;
	public static boolean defaultRegisterReferenceFirst = false;
	
	final protected String warningLabel = " (WARNING: Only available for "; 
	
	static
	{
		IOFunctions.printIJLog = true;
		staticAlgorithms.add( new GeometricHashing3d( null, null, null, null, null ) );
		staticAlgorithms.add( new RGLDM( null, null, null, null, null ) );
	}

	@Override
	public void run( final String arg )
	{
		new ImageJ();
		
		// ask for everything but the channels
		final XMLParseResult result = new LoadParseQueryXML().queryXML( true, false, true, true );

		if ( result == null )
			return;
		
		// the GenericDialog needs a list[] of String for the algorithms that can register
		final String[] descriptions = new String[ staticAlgorithms.size() ];
		
		for ( int i = 0; i < staticAlgorithms.size(); ++i )
			descriptions[ i ] = staticAlgorithms.get( i ).getDescription();
		
		if ( defaultAlgorithm >= descriptions.length )
			defaultAlgorithm = 0;

		// ask which channels have the objects we are searching for
		final ArrayList< Channel > channels = result.getData().getSequenceDescription().getAllChannels();

		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Basic Registration Parameters" );
		
		gd.addChoice( "Registration_algorithm", descriptions, descriptions[ defaultAlgorithm ] );
		
		final String[] choicesGlobal;
		if ( result.getTimePointsToProcess().size() > 1 )
			choicesGlobal = registrationChoices.clone();
		else
			choicesGlobal = new String[]{ registrationChoices[ 0 ], registrationChoices[ 1 ] };
		
		if ( defaultRegistration >= choicesGlobal.length )
			defaultRegistration = 0;
		
		gd.addChoice( "Type_of_registration", choicesGlobal, choicesGlobal[ defaultRegistration ] );
		
		if ( defaultChannelLabels == null || defaultChannelLabels.length != channels.size() )
			defaultChannelLabels = new int[ channels.size() ];
		
		// check which channels and labels are available and build the choices
		final ArrayList< String[] > channelLabels = new ArrayList< String[] >();
		int i = 0;
		for ( final Channel channel : channels )
		{
			final String[] labels = getAllInterestPointLabelsForChannel( result.getData(), result.getTimePointsToProcess(), channel );
			
			if ( channelLabels == null )
				return;
			
			if ( defaultChannelLabels[ channel.getId() ] >= labels.length )
				defaultChannelLabels[ channel.getId() ] = 0;
			
			gd.addChoice( "Interest_points_channel_" + channel.getName(), labels, labels[ defaultChannelLabels[ i++ ] ] );
			channelLabels.add( labels );
		}
		GUIHelper.addWebsite( gd );
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		final int algorithm = defaultAlgorithm = gd.getNextChoiceIndex();
		final int registrationType = defaultRegistration = gd.getNextChoiceIndex();

		// assemble which channels have been selected with with label
		final ArrayList< ChannelProcess > channelsToProcess = new ArrayList< ChannelProcess >();
		i = 0;
		
		for ( final Channel channel : channels )
		{
			final int channelChoice = defaultChannelLabels[ channel.getId() ] = gd.getNextChoiceIndex();
			
			if ( channelChoice < channelLabels.get( i ).length - 1 )
			{
				String label = channelLabels.get( i++ )[ channelChoice ];
				
				if ( label.contains( warningLabel ) )
					label = label.substring( 0, label.indexOf( warningLabel ) );
				
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
		
		final InterestPointRegistration ipr = staticAlgorithms.get( algorithm ).newInstance(
				result.getData(),
				result.getAnglesToProcess(),
				channelsToProcess,
				result.getIlluminationsToProcess(),
				result.getTimePointsToProcess());

		// call the next dialog that asks for specific details
		register( ipr, result, registrationType );
	}
	
	public static String[] inputChoice = new String[]{ "Calibration only (resets existing transform)", "Current view transformations (appends to current transform)" };	
	public static int defaultTransformInputChoice = 0;
	public static boolean defaultRemoveExistingCorrespondences = true;
	public static boolean defaultAddNewCorrespondences = true;
	public static boolean defaultDisplayTransformOnly = false;
	
	protected String[] assembleTimepoints( final TimePoints< TimePoint > timepoints )
	{
		final String[] tps = new String[ timepoints.getTimePointList().size() ];
		
		for ( int t = 0; t < tps.length; ++t )
			tps[ t ] = timepoints.getTimePointList().get( t ).getName();
		
		return tps;
	}
	
	protected void register( final InterestPointRegistration ipr, final XMLParseResult result, final int registrationType )
	{
		final GenericDialog gd = new GenericDialog( "Register: " + registrationChoices[ registrationType ] );
		
		if ( registrationType == 1 )
		{
			final String[] tpList = assembleTimepoints( result.getData().getSequenceDescription().getTimePoints() );
			
			// by default, the reference timepoint is the first one the ones to process
			if ( defaultReferenceTimepoint < 0 || defaultReferenceTimepoint >= tpList.length )
				defaultReferenceTimepoint = result.getIlluminationsToProcess().get( 0 ).getId();

			// if that goes wrong by any chance (should not), set it to 0
			if ( defaultReferenceTimepoint >= tpList.length )
				defaultReferenceTimepoint = 0;

			gd.addChoice( "Reference timepoint", tpList, tpList[ defaultReferenceTimepoint ] );
			gd.addCheckbox( "Register reference timepoint first", defaultRegisterReferenceFirst );
			gd.addMessage( "" );
		}
		else if ( registrationType == 3 )
		{
			gd.addSlider( "Range for all-to-all timepoint matching", 2, 10, defaultRange );
		}
		
		gd.addChoice( "Register_based_on", inputChoice, inputChoice[ defaultTransformInputChoice ] );
		gd.addCheckbox( "Remove_existing_correspondences (from previous registrations)", defaultRemoveExistingCorrespondences );
		gd.addCheckbox( "Add_new_correspondences (as identified by this registration run)", defaultAddNewCorrespondences );
		gd.addCheckbox( "Display final transformation only (do not edit XML)", defaultDisplayTransformOnly );
		
		gd.addMessage( "" );
		gd.addMessage( "Algorithm parameters [" + ipr.getDescription() + "]", new Font( Font.SANS_SERIF, Font.BOLD, 12 ) );
		gd.addMessage( "" );
		
		ipr.addQuery( gd, registrationType );
		
		// display the dialog
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;

		boolean registerReferenceFirst = defaultRegisterReferenceFirst;
		int referenceTimePoint = defaultReferenceTimepoint;
		int range = defaultRange;
		
		if ( registrationType == 1 )
		{
			referenceTimePoint = defaultReferenceTimepoint = gd.getNextChoiceIndex();
			registerReferenceFirst = defaultRegisterReferenceFirst = gd.getNextBoolean();
		}

		if ( registrationType == 3 )
			range = defaultRange = (int)Math.round( gd.getNextNumber() );
		
		ipr.setInitialTransformType( defaultTransformInputChoice = gd.getNextChoiceIndex() );
		final boolean removeCorrespondences = defaultRemoveExistingCorrespondences = gd.getNextBoolean();
		final boolean addNewCorrespondences = defaultAddNewCorrespondences = gd.getNextBoolean();
		final boolean displayOnly = defaultDisplayTransformOnly = gd.getNextBoolean();
		
		ipr.parseDialog( gd, registrationType );
		
		// first register only the reference timepoint if wanted
		if ( registrationType == 1 && registerReferenceFirst )
		{
			if ( displayOnly )
			{
				IOFunctions.println( "Not writing the results and registering the reference timepoint first is not possible." );
				IOFunctions.println( "The result of the reference timepoint registration must be written to disc in order to take effect" );
				return;
			}
			else
			{
				IOFunctions.println( "Registering reference timepoint: " + 
						result.getData().getSequenceDescription().getTimePoints().getTimePointList().get( referenceTimePoint ).getName() +
						", id: " + referenceTimePoint );
			}

			// save all timepoints that need to be processed
			final ArrayList< TimePoint > tps = new ArrayList< TimePoint >();
			tps.addAll( ipr.getTimepointsToProcess() );
			ipr.getTimepointsToProcess().clear();
			ipr.getTimepointsToProcess().add( result.getData().getSequenceDescription().getTimePoints().getTimePointList().get( referenceTimePoint ) );
			
			// only individually register the reference timepoint
			ipr.register( new IndividualTimepointRegistration( removeCorrespondences, addNewCorrespondences, !displayOnly ) );
			
			// restore the timepoints and save XML
			ipr.getTimepointsToProcess().clear();
			ipr.getTimepointsToProcess().addAll( tps );
			
			if ( !displayOnly )
				saveXML( result );
		}
		
		// perform the actual registration(s)
		final GlobalOptimizationType type;
		
		if ( registrationType == 0 )
			type = new IndividualTimepointRegistration( removeCorrespondences, addNewCorrespondences, !displayOnly );
		else if ( registrationType == 1 )
			type = new ReferenceTimepointRegistration(
					result.getData(),
					ipr.getAnglesToProcess(),
					ipr.getChannelsToProcess(),
					ipr.getIllumsToProcess(),
					result.getData().getSequenceDescription().getTimePoints().getTimePointList().get( referenceTimePoint ),
					removeCorrespondences, addNewCorrespondences, !displayOnly );
		else if ( registrationType == 2 )
			type = new AllToAllRegistration( removeCorrespondences, addNewCorrespondences, !displayOnly );
		else if ( registrationType == 3 )
			type = new AllToAllRegistrationWithRange( range, removeCorrespondences, addNewCorrespondences, !displayOnly );
		else
			type = null;
		
		if ( !ipr.register( type ) )
			return;
				
		// save the XML including transforms and correspondences
		if ( !displayOnly )
			saveXML( result );
	}
	
	protected void saveXML( final XMLParseResult result )
	{
		// save the xml
		final XmlIoSpimData2 io = XmlIo.createDefaultIo();
		final SpimData2 data = result.getData();
		
		final String xml = new File( data.getBasePath(), new File( result.getXMLFileName() ).getName() ).getAbsolutePath();
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
	 * Goes through all ViewDescriptions and checks all available labels for interest point detection
	 * 
	 * @param spimData
	 * @param timepointsToProcess
	 * @param channel
	 * @return
	 */
	protected String[] getAllInterestPointLabelsForChannel( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final Channel channel )
	{
		final ViewInterestPoints interestPoints = spimData.getViewInterestPoints();
		final HashMap< String, Integer > labels = new HashMap< String, Integer >();
		
		int countViewDescriptions = 0;
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Angle a : spimData.getSequenceDescription().getAllAngles() )
				for ( final Illumination i : spimData.getSequenceDescription().getAllIlluminations() )
				{
					final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, channel, a, i );
					
					// could the viewid be resolved? this should always work
					if ( viewId == null )
					{
						IOFunctions.println( "An error occured. Count not find the corresponding ViewSetup for angle: " + 
							a.getId() + " channel: " + channel.getId() + " illum: " + i.getId() );
						
						return null;
					}
					
					// get the viewdescription
					final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
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
		
		final String[] allLabels = new String[ labels.keySet().size() + 1 ];
		
		int i = 0;
		
		for ( final String label : labels.keySet() )
		{
			allLabels[ i ] = label;
			
			if ( labels.get( label ) != countViewDescriptions )
				allLabels[ i ] += warningLabel + labels.get( label ) + "/" + countViewDescriptions + " Views!)";
			
			++i;
		}
		
		allLabels[ i ] = "[DO NOT register this channel]";
		
		return allLabels;
	}

	public static void main( String[] args )
	{
		new Interest_Point_Registration().run( null );
	}
}