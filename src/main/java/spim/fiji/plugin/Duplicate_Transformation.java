package spim.fiji.plugin;

import ij.ImageJ;
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

	public static String[] transformationChoice = new String[]{
		"Replace all transformations",
		"Add last transformation only",
		"Add multiple transformations"
	};

	public static int defaultChoice = 0;
	public static int defaultTransformationChoice = 0;
	public static int defaultNumTransformations = 2;
	public static int defaultTimePoint = 0;
	public static int defaultSelectedTimePointIndex = 1;
	public static int defaultChannel = 0;
	public static int defaultSelectedChannelIndex = 1;
	public static int defaultIllum = 0;
	public static int defaultSelectedIllumIndex = 1;
	public static int defaultAngle = 0;
	public static int defaultSelectedAngleIndex = 1;

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
		else if ( !askForChannels )
		{
			if ( result.getChannelsToProcess().size() == 1 )
			{
				IOFunctions.println( "Only one channel available, cannot apply to another channel." );
				return;
			}
			else
			{
				if ( !applyChannels( result ) )
					return;
			}			
		}
		else if ( !askForIllum )
		{
			if ( result.getIlluminationsToProcess().size() == 1 )
			{
				IOFunctions.println( "Only one illumination direction available, cannot apply to another illumination direction." );
				return;
			}
			else
			{
				if ( !applyIllums( result ) )
					return;
			}			
		}
		else if ( !askForAngles )
		{
			if ( result.getAnglesToProcess().size() == 1 )
			{
				IOFunctions.println( "Only one angle available, cannot apply to another angle." );
				return;
			}
			else
			{
				if ( !applyAngles( result ) )
					return;
			}			
		}

		// now save it in case something was applied
		Interest_Point_Registration.saveXML( result.getData(), result.getXMLFileName() );
	}

	protected void askForRegistrations( final GenericDialog gd )
	{
		gd.addMessage( "" );
		gd.addChoice( "Duplicate_which_transformations", transformationChoice, transformationChoice[ defaultTransformationChoice ] );
	}

	/**
	 * 
	 * @param gd
	 * @return -1 means invalid/cancelled, 0 means all, >0 means how many
	 */
	protected int parseRegistrations( final GenericDialog gd )
	{
		int transformation = defaultTransformationChoice = gd.getNextChoiceIndex();
		
		if ( transformation == 2 )
		{
			final GenericDialog gd2 = new GenericDialog( "Choose number of transformations" );
			gd2.addNumericField( "Number of transformations to add", defaultNumTransformations, 0 );
			
			gd2.showDialog();
			
			if ( gd2.wasCanceled() )
				return -1;
			else
				transformation = (int)Math.round( gd2.getNextNumber() );
		}
		
		return transformation;
	}

	protected void duplicateTransformations( final int transformations, final ViewId sourceViewId, final ViewId targetViewId, final SpimData2 spimData )
	{
		final ViewDescription sourceVD = spimData.getSequenceDescription().getViewDescription( 
				sourceViewId.getTimePointId(), sourceViewId.getViewSetupId() );

		final ViewDescription targetVD = spimData.getSequenceDescription().getViewDescription( 
				targetViewId.getTimePointId(), targetViewId.getViewSetupId() );

		final ViewSetup sourceVS = sourceVD.getViewSetup();
		final ViewSetup targetVS = targetVD.getViewSetup();

		IOFunctions.println( "Source viewId t=" + sourceVD.getTimePoint().getName() + ", ch=" + sourceVS.getChannel().getName() + ", ill=" + sourceVS.getIllumination().getName() + ", angle=" + sourceVS.getAngle().getName() );
		IOFunctions.println( "Target viewId t=" + targetVD.getTimePoint().getName() + ", ch=" + targetVS.getChannel().getName() + ", ill=" + targetVS.getIllumination().getName() + ", angle=" + targetVS.getAngle().getName() );  
		
		if ( !sourceVD.isPresent() || !targetVD.isPresent() )
		{
			if ( !sourceVD.isPresent() )
				IOFunctions.println( "Source viewId is NOT present" );
			
			if ( !targetVD.isPresent() )
				IOFunctions.println( "Target viewId is NOT present" );
			
			return;
		}
		
		// update the view registration
		final ViewRegistrations viewRegistrations = spimData.getViewRegistrations();
		
		final ViewRegistration vrSource = viewRegistrations.getViewRegistration( sourceViewId );
		final ViewRegistration vrTarget = viewRegistrations.getViewRegistration( targetViewId );
		
		// reset the transformation and add all
		if ( transformations == 0 )
		{
			vrTarget.identity();

			for ( final ViewTransform vt : vrSource.getTransformList() )
			{
				IOFunctions.println( "Concatenationg model " + vt.getName() + ", " + vt.asAffine3D() );
				vrTarget.concatenateTransform( vt );
			}
		}
		else
		{
			// copy the last n transformations
			final ArrayList< ViewTransform > vts = new ArrayList< ViewTransform >();
			for ( int k = 0; k < transformations; ++k )
				vts.add( vrSource.getTransformList().get( k ) );
			
			// and add them at the end
			for ( int k = vts.size() - 1; k >= 0; --k )
			{
				final ViewTransform vt = vts.get( k );
				IOFunctions.println( "Adding model " + vt.getName() + ", " + vt.asAffine3D() );
				vrTarget.preconcatenateTransform( vt );
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

		askForRegistrations( gd );

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
			final int transformations = parseRegistrations( gd );

			if ( transformations < 0 )
				return false;

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
								
								duplicateTransformations( transformations, sourceViewId, targetViewId, result.getData() );
							}
				}
			
			if ( countApplied == 0 )
				return false;
		}
		return true;
	}

	protected boolean applyChannels( final XMLParseResult result )
	{
		final GenericDialog gd = new GenericDialog( "Define source and target channels" );
		
		final String[] channels = assembleChannels( result.getChannelsToProcess() );
		
		if ( defaultChannel >= channels.length )
			defaultChannel = 0;
		
		gd.addChoice( "Source channel", channels, channels[ defaultChannel ] );
		gd.addChoice( "Target channel(s)", LoadParseQueryXML.channelChoice, LoadParseQueryXML.channelChoice[ LoadParseQueryXML.defaultChannelChoice ] );
		
		askForRegistrations( gd );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final Channel source = result.getChannelsToProcess().get( defaultChannel = gd.getNextChoiceIndex() );
		final ArrayList< Channel > targets = new ArrayList< Channel >();
		
		final int choice = LoadParseQueryXML.defaultChannelChoice = gd.getNextChoiceIndex();
		
		if ( choice == 1 )
		{
			if ( defaultSelectedChannelIndex >= channels.length )
				defaultSelectedChannelIndex = 1;
			
			final int selection = LoadParseQueryXML.queryIndividualEntry( "Channel", channels, defaultSelectedChannelIndex );
			
			if ( selection >= 0 )
				targets.add( result.getChannelsToProcess().get( defaultSelectedChannelIndex = selection ) );
			else
				return false;
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple channels or channels defined by pattern
		{
			final boolean[] selection;
			String[] defaultChannel = new String[]{ LoadParseQueryXML.defaultChannelString };
			
			if ( choice == 2 )
				selection = LoadParseQueryXML.queryMultipleEntries( "Channels", channels, LoadParseQueryXML.defaultChannelIndices );
			else
				selection = LoadParseQueryXML.queryPattern( "Channels", channels, defaultChannel );
			
			if ( selection == null )
				return false;
			else
			{
				LoadParseQueryXML.defaultChannelIndices = selection;
				
				if ( choice == 3 )
					LoadParseQueryXML.defaultChannelString = defaultChannel[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						targets.add( result.getChannelsToProcess().get( i ) );
			}
		}
		else
		{
			targets.addAll( result.getChannelsToProcess() );				
		}
		
		if ( targets.size() == 0 )
		{
			IOFunctions.println( "List of channels is empty. Stopping." );
			return false;
		}
		else
		{
			final int transformations = parseRegistrations( gd );
			
			if ( transformations < 0 )
				return false;
			
			int countApplied = 0;
			
			for ( int j = 0; j < targets.size(); ++j )
				if ( !source.equals( targets.get( j ) ) )
				{
					IOFunctions.println( "Applying chanel " + source.getName() + " >>> " + targets.get( j ).getName() );
					++countApplied;
					
					for ( final TimePoint t : result.getTimePointsToProcess() )
						for ( final Illumination i : result.getIlluminationsToProcess() )
							for ( final Angle a : result.getAnglesToProcess() )
							{
								final ViewId sourceViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, source, a, i );
								final ViewId targetViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, targets.get( j ), a, i );
								
								duplicateTransformations( transformations, sourceViewId, targetViewId, result.getData() );
							}
				}
			
			if ( countApplied == 0 )
				return false;
		}
		return true;
	}

	protected boolean applyIllums( final XMLParseResult result )
	{
		final GenericDialog gd = new GenericDialog( "Define source and target illumination directions" );
		
		final String[] illums = assembleIllums( result.getIlluminationsToProcess() );
		
		if ( defaultIllum >= illums.length )
			defaultIllum = 0;
		
		gd.addChoice( "Source illumination direction", illums, illums[ defaultIllum ] );
		gd.addChoice( "Target illumination direction(s)", LoadParseQueryXML.illumChoice, LoadParseQueryXML.illumChoice[ LoadParseQueryXML.defaultIllumChoice ] );

		askForRegistrations( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final Illumination source = result.getIlluminationsToProcess().get( defaultIllum = gd.getNextChoiceIndex() );
		final ArrayList< Illumination > targets = new ArrayList< Illumination >();
		
		final int choice = LoadParseQueryXML.defaultIllumChoice = gd.getNextChoiceIndex();
		
		if ( choice == 1 )
		{
			if ( defaultSelectedIllumIndex >= illums.length )
				defaultSelectedIllumIndex = 1;
			
			final int selection = LoadParseQueryXML.queryIndividualEntry( "Illumination direction", illums, defaultSelectedIllumIndex );
			
			if ( selection >= 0 )
				targets.add( result.getIlluminationsToProcess().get( defaultSelectedIllumIndex = selection ) );
			else
				return false;
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple illum dir or illum dirs defined by pattern
		{
			final boolean[] selection;
			String[] defaultIllum = new String[]{ LoadParseQueryXML.defaultIllumString };
			
			if ( choice == 2 )
				selection = LoadParseQueryXML.queryMultipleEntries( "Illumination directions", illums, LoadParseQueryXML.defaultIllumIndices );
			else
				selection = LoadParseQueryXML.queryPattern( "Illumination directions", illums, defaultIllum );
			
			if ( selection == null )
				return false;
			else
			{
				LoadParseQueryXML.defaultIllumIndices = selection;
				
				if ( choice == 3 )
					LoadParseQueryXML.defaultIllumString = defaultIllum[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						targets.add( result.getIlluminationsToProcess().get( i ) );
			}
		}
		else
		{
			targets.addAll( result.getIlluminationsToProcess() );				
		}
		
		if ( targets.size() == 0 )
		{
			IOFunctions.println( "List of illumination directions is empty. Stopping." );
			return false;
		}
		else
		{
			final int transformations = parseRegistrations( gd );
			
			if ( transformations < 0 )
				return false;

			int countApplied = 0;
			
			for ( int j = 0; j < targets.size(); ++j )
				if ( !source.equals( targets.get( j ) ) )
				{
					IOFunctions.println( "Applying illumination direction " + source.getName() + " >>> " + targets.get( j ).getName() );
					++countApplied;
					
					for ( final TimePoint t : result.getTimePointsToProcess() )
						for ( final Channel c : result.getChannelsToProcess() )
							for ( final Angle a : result.getAnglesToProcess() )
							{
								final ViewId sourceViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, a, source );
								final ViewId targetViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, a, targets.get( j ) );
								
								duplicateTransformations( transformations, sourceViewId, targetViewId, result.getData() );
							}
				}
			
			if ( countApplied == 0 )
				return false;
		}
		return true;
	}

	protected boolean applyAngles( final XMLParseResult result )
	{
		final GenericDialog gd = new GenericDialog( "Define source and target angles" );
		
		final String[] angles = assembleAngles( result.getAnglesToProcess() );
		
		if ( defaultAngle >= angles.length )
			defaultAngle = 0;
		
		gd.addChoice( "Source angle", angles, angles[ defaultAngle ] );
		gd.addChoice( "Target angles(s)", LoadParseQueryXML.angleChoice, LoadParseQueryXML.angleChoice[ LoadParseQueryXML.defaultAngleChoice ] );

		askForRegistrations( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final Angle source = result.getAnglesToProcess().get( defaultAngle = gd.getNextChoiceIndex() );
		final ArrayList< Angle > targets = new ArrayList< Angle >();
		
		final int choice = LoadParseQueryXML.defaultAngleChoice = gd.getNextChoiceIndex();
		
		if ( choice == 1 )
		{
			if ( defaultSelectedAngleIndex >= angles.length )
				defaultSelectedAngleIndex = 1;
			
			final int selection = LoadParseQueryXML.queryIndividualEntry( "Angle", angles, defaultSelectedAngleIndex );
			
			if ( selection >= 0 )
				targets.add( result.getAnglesToProcess().get( defaultSelectedAngleIndex = selection ) );
			else
				return false;
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple angle or angles defined by pattern
		{
			final boolean[] selection;
			String[] defaultAngle = new String[]{ LoadParseQueryXML.defaultAngleString };
			
			if ( choice == 2 )
				selection = LoadParseQueryXML.queryMultipleEntries( "Angles", angles, LoadParseQueryXML.defaultAngleIndices );
			else
				selection = LoadParseQueryXML.queryPattern( "Angles", angles, defaultAngle );
			
			if ( selection == null )
				return false;
			else
			{
				LoadParseQueryXML.defaultAngleIndices = selection;
				
				if ( choice == 3 )
					LoadParseQueryXML.defaultAngleString = defaultAngle[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						targets.add( result.getAnglesToProcess().get( i ) );
			}
		}
		else
		{
			targets.addAll( result.getAnglesToProcess() );				
		}
		
		if ( targets.size() == 0 )
		{
			IOFunctions.println( "List of angles is empty. Stopping." );
			return false;
		}
		else
		{
			final int transformations = parseRegistrations( gd );

			if ( transformations < 0 )
				return false;

			int countApplied = 0;
			
			for ( int j = 0; j < targets.size(); ++j )
				if ( !source.equals( targets.get( j ) ) )
				{
					IOFunctions.println( "Applying angle " + source.getName() + " >>> " + targets.get( j ).getName() );
					++countApplied;
					
					for ( final TimePoint t : result.getTimePointsToProcess() )
						for ( final Channel c : result.getChannelsToProcess() )
							for ( final Illumination i : result.getIlluminationsToProcess() )
							{
								final ViewId sourceViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, source, i );
								final ViewId targetViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, targets.get( j ), i );
								
								duplicateTransformations( transformations, sourceViewId, targetViewId, result.getData() );
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

	protected String[] assembleChannels( final List< Channel > channels )
	{
		final String[] chs = new String[ channels.size() ];
		
		for ( int t = 0; t < chs.length; ++t )
			chs[ t ] = channels.get( t ).getName();
		
		return chs;
	}

	protected String[] assembleIllums( final List< Illumination > illums )
	{
		final String[] is = new String[ illums.size() ];
		
		for ( int t = 0; t < is.length; ++t )
			is[ t ] = illums.get( t ).getName();
		
		return is;
	}

	protected String[] assembleAngles( final List< Angle > angles )
	{
		final String[] as = new String[ angles.size() ];
		
		for ( int t = 0; t < as.length; ++t )
			as[ t ] = angles.get( t ).getName();
		
		return as;
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new Duplicate_Transformation().run( null );
	}
}
