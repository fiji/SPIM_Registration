package spim.fiji.plugin;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.spimdata.SpimData2;

public class Duplicate_Transformation implements PlugIn
{
	public static String[] duplicationChoice = new String[]{
		"One timepoint to other timepoints",
		"One channel to other channels",
		"One illumination direction to other illumination directions",
		"One angle to other angles",
	};
	
	public static int defaultChoice = 0;
	public static int defaultTimePoint = 0;
	public static int defaultSelectedTimePointIndex = 1;
	
	@Override
	public void run( final String arg0 )
	{
		final GenericDialog gd = new GenericDialog( "Define Duplication" );
		gd.addChoice( "Apply transformation of", duplicationChoice, duplicationChoice[ defaultChoice ] );
		gd.showDialog();
		if ( gd.wasCanceled() )
			return;
		
		final int choice = defaultChoice = gd.getNextChoiceIndex();

		final boolean askForTimepoints = choice != 0;
		final boolean askForChannels = choice != 1;
		final boolean askForIllum = choice != 2;
		final boolean askForAngles = choice != 3;

		final XMLParseResult result = new LoadParseQueryXML().queryXML( "duplicating transformations", "Apply to", askForAngles, askForChannels, askForIllum, askForTimepoints );
		
		if ( result == null )
			return;
		
		if ( !askForTimepoints )
		{
			if ( result.getTimePointsToProcess().size() == 1 )
			{
				IOFunctions.println( "Only one timepoint available, cannot apply to another timepoint." );
				return;
			}
			else
			{
				if ( !applyTimepoints( result ) )
					return;
			}
		}

		// now save it in case something was applied
		Interest_Point_Registration.saveXML( result.getData(), result.getXMLFileName() );
	}
	
	protected boolean applyTimepoints( final XMLParseResult result )
	{
		final GenericDialog gd = new GenericDialog( "Define source and target timepoints" );
		
		final String[] timepoints = assembleTimepoints( result.getTimePointsToProcess() );
		
		if ( defaultTimePoint >= timepoints.length )
			defaultTimePoint = 0;
		
		gd.addChoice( "Source timepoint", timepoints, timepoints[ defaultTimePoint ] );
		gd.addChoice( "Target timepoint(s)", LoadParseQueryXML.tpChoice, LoadParseQueryXML.tpChoice[ LoadParseQueryXML.defaultTPChoice ] );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final TimePoint source = result.getTimePointsToProcess().get( defaultTimePoint = gd.getNextChoiceIndex() );
		final ArrayList< TimePoint > targets = new ArrayList< TimePoint >();
		
		final int choice = LoadParseQueryXML.defaultTPChoice = gd.getNextChoiceIndex();
		
		if ( choice == 1 )
		{
			if ( defaultSelectedTimePointIndex >= timepoints.length )
				defaultSelectedTimePointIndex = 1;
			
			final int selection = LoadParseQueryXML.queryIndividualEntry( "Timepoint", timepoints, defaultSelectedTimePointIndex );
			
			if ( selection >= 0 )
				targets.add( result.getTimePointsToProcess().get( defaultSelectedTimePointIndex = selection ) );
			else
				return false;
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple timepoints or timepoints defined by pattern
		{
			final boolean[] selection;
			String[] defaultTimePoint = new String[]{ LoadParseQueryXML.defaultTimePointString };
			
			if ( choice == 2 )
				selection = LoadParseQueryXML.queryMultipleEntries( "Timepoints", timepoints, LoadParseQueryXML.defaultTimePointIndices );
			else
				selection = LoadParseQueryXML.queryPattern( "Timepoints", timepoints, defaultTimePoint );
			
			if ( selection == null )
				return false;
			else
			{
				LoadParseQueryXML.defaultTimePointIndices = selection;
				
				if ( choice == 3 )
					LoadParseQueryXML.defaultTimePointString = defaultTimePoint[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						targets.add( result.getTimePointsToProcess().get( i ) );
			}
		}
		else
		{
			targets.addAll( result.getTimePointsToProcess() );				
		}
		
		if ( targets.size() == 0 )
		{
			IOFunctions.println( "List of timepoints is empty. Stopping." );
			return false;
		}
		else
		{
			final ViewRegistrations viewRegistrations = result.getData().getViewRegistrations();

			int countApplied = 0;
			
			for ( int j = 0; j < targets.size(); ++j )
				if ( !source.equals( targets.get( j ) ) )
				{
					IOFunctions.println( "Applying timepoint " + source.getName() + " >>> " + targets.get( j ).getName() );
					++countApplied;
					
					for ( final Channel c : result.getChannelsToProcess() )
						for ( final Illumination i : result.getIlluminationsToProcess() )
							for ( final Angle a : result.getAnglesToProcess() )
							{
								final ViewId sourceViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), source, c, a, i );
								final ViewId targetViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), targets.get( j ), c, a, i );
								
								final ViewDescription< TimePoint, ViewSetup > sourceViewDescription = result.getData().getSequenceDescription().getViewDescription( 
										sourceViewId.getTimePointId(), sourceViewId.getViewSetupId() );

								final ViewDescription< TimePoint, ViewSetup > targetViewDescription = result.getData().getSequenceDescription().getViewDescription( 
										targetViewId.getTimePointId(), targetViewId.getViewSetupId() );

								IOFunctions.println( "Source viewId t=" + source.getName() + ", ch=" + c.getName() + ", ill=" + i.getName() + ", angle=" + a.getName() );  
								IOFunctions.println( "Target viewId t=" + targets.get( j ).getName() + ", ch=" + c.getName() + ", ill=" + i.getName() + ", angle=" + a.getName() );  
								
								if ( !sourceViewDescription.isPresent() || !targetViewDescription.isPresent() )
								{
									if ( !sourceViewDescription.isPresent() )
										IOFunctions.println( "Source viewId is NOT present" );
									
									if ( !targetViewDescription.isPresent() )
										IOFunctions.println( "Target viewId is NOT present" );
									
									continue;
								}
								
								// update the view registration
								final ViewRegistration vrSource = viewRegistrations.getViewRegistration( sourceViewId );
								final ViewRegistration vrTarget = viewRegistrations.getViewRegistration( targetViewId );
								
								vrTarget.identity();
								
								for ( final ViewTransform vt : vrSource.getTransformList() )
								{
									IOFunctions.println( "Concatenationg model " + vt.getName() + ", " + vt.asAffine3D() );
									vrTarget.concatenateTransform( vt );
								}
							}
				}
			
			if ( countApplied == 0 )
				return false;
		}
		return true;
	}
	
	protected String[] assembleTimepoints( final List< TimePoint > timepoints )
	{
		final String[] tps = new String[ timepoints.size() ];
		
		for ( int t = 0; t < tps.length; ++t )
			tps[ t ] = timepoints.get( t ).getName();
		
		return tps;
	}

	public static void main( final String[] args )
	{
		new Duplicate_Transformation().run( null );
	}
}
