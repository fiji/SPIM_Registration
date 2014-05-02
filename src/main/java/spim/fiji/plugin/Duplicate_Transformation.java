package spim.fiji.plugin;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;

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
			for ( int i = 0; i < targets.size(); ++i )
				if ( !source.equals( targets.get( i ) ) )
				{
					IOFunctions.println( "Applying timepoint " + source.getName() + " >>> " + targets.get( i ).getName() );
					
					// TODO: apply
				}
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
