package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.io.IOFunctions;

import org.jdom2.JDOMException;

import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIo;
import spim.fiji.spimdata.XmlIoSpimData2;
import fiji.util.gui.GenericDialogPlus;

public class LoadParseQueryXML 
{
	public static String defaultXMLfilename = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM/example_fromdialog.xml";
		
	public static String goodMsg1 = "The selected XML file was parsed successfully";
	public static String warningMsg1 = "The selected file does not appear to be an xml. Press OK to try to parse anyways.";
	public static String errorMsg1 = "An ERROR occured parsing this XML file! Please select a different XML (see log)";
	public static String neutralMsg1 = "No XML file selected.";
	
	public static String noMsg2 = " \n ";
	
	public static String[] tpChoice = new String[]{ "All Timepoints", "Single Timepoint (Select from List)", "Multiple Timepoints (Select from List)", "Range of Timepoints (Specify by Name)" };
	public static int defaultTPChoice = 0;
	public static int defaultTimePointIndex = 0;
	public static boolean[] defaultTimePointIndices = null;
	public static String defaultTimePointString = null;

	public static String[] angleChoice = new String[]{ "All Angles", "Single Angle (Select from List)", "Multiple Angles (Select from List)", "Range of Angles (Specify by Name)" };
	public static int defaultAngleChoice = 0;
	public static int defaultAngleIndex = 0;
	public static boolean[] defaultAngleIndices = null;
	public static String defaultAngleString = null;

	public static String[] channelChoice = new String[]{ "All Channels", "Single Channel (Select from List)", "Multiple Channels (Select from List)", "Range of Channels (Specify by Name)" };
	public static int defaultChannelChoice = 0;
	public static int defaultChannelIndex = 0;
	public static boolean[] defaultChannelIndices = null;
	public static String defaultChannelString = null;

	public static String[] illumChoice = new String[]{ "All Illumination Directions", "Single Illumination Directions (Select from List)", "Multiple Illumination Directions (Select from List)", "Range of Illumination Directions (Specify by Name)" };
	public static int defaultIllumChoice = 0;
	public static int defaultIllumIndex = 0;
	public static boolean[] defaultIllumIndices = null;
	public static String defaultIllumString = null;

	public class XMLParseResult
	{
		// local variables for LoadParseQueryXML
		String message1, message2;
		Color color;
		int timepointChoiceIndex;
		
		// global variables
		private SpimData2 data;
		private String xmlfilename;
		private ArrayList< TimePoint > timepoints;
		private ArrayList< Angle > angles;
		private ArrayList< Channel > channels;
		private ArrayList< Illumination > illums;
		
		public int timepointAngleIndex, channelChoiceIndex, illumChoiceIndex, angleChoiceIndex;
		
		/**
		 * @return the SpimDataBeads object parsed from the xml
		 */
		public SpimData2 getData() { return data; }
		
		/**
		 * @return The location of the xml file
		 */
		public String getXMLFileName() { return xmlfilename; }
		
		/**
		 * @return All timepoints that should be processed
		 */
		public ArrayList< TimePoint > getTimePointsToProcess() { return timepoints; }
		
		/**
		 * @return All angles that should be processed
		 */
		public ArrayList< Angle > getAnglesToProcess() { return angles; }

		/**
		 * @return All channels that should be processed
		 */
		public ArrayList< Channel > getChannelsToProcess() { return channels; }

		/**
		 * @return All illumination directions that should be processed
		 */
		public ArrayList< Illumination > getIlluminationsToProcess() { return illums; }
	}

	public XMLParseResult queryXML(
			final String additionalTitle,
			final boolean askForAngles,
			final boolean askForChannels,
			final boolean askForIllum,
			final boolean askForTimepoints )
	{
		return queryXML( additionalTitle, "Process", askForAngles, askForChannels, askForIllum, askForTimepoints );
	}

	public XMLParseResult queryXML(
			final boolean askForAngles,
			final boolean askForChannels,
			final boolean askForIllum,
			final boolean askForTimepoints )
	{
		return queryXML( "", "Process", askForAngles, askForChannels, askForIllum, askForTimepoints );
	}
	
	/**
	 * Asks the user for a valid XML (real time parsing)
	 * 
	 * @param askForAngles - ask the user if he/she wants to select a subset of angles, otherwise all angles are selected
	 * @param askForChannels - ask the user if he/she wants to select a subset of channels, otherwise all channels are selected
	 * @param askForIllum - ask the user if he/she wants to select a subset of illuminations, otherwise all illuminations are selected
	 * @param askForTimepoints - ask the user if he/she wants to select a subset of timepoints, otherwise all timepoints are selected
	 * @return null if cancelled or timepointlistsize = 0
	 */
	public XMLParseResult queryXML(
			final String additionalTitle,
			String query,
			final boolean askForAngles,
			final boolean askForChannels,
			final boolean askForIllum,
			final boolean askForTimepoints )
	{
		// adjust query to support recording
		if ( query.contains( " " ) )
			query = query.replace( " ", "_" );
		
		// try parsing if it ends with XML
		XMLParseResult xmlResult = tryParsing( defaultXMLfilename, false );
		
		final GenericDialogPlus gd;
		
		if ( additionalTitle != null && additionalTitle.length() > 0 )
			gd = new GenericDialogPlus( "Select dataset for " + additionalTitle );
		else
			gd = new GenericDialogPlus( "Select Dataset" );
		gd.addFileField( "Select_XML", defaultXMLfilename, 65 );
		gd.addMessage( xmlResult.message1, GUIHelper.largestatusfont, xmlResult.color );
		Label l1 = (Label)gd.getMessage();
		
		// first set an empty text so that it does not become a multilinelabel
		gd.addMessage( "", GUIHelper.smallStatusFont, xmlResult.color );
		Label l2 = (Label)gd.getMessage();
		l2.setText( xmlResult.message2 );
		addListeners( gd, (TextField)gd.getStringFields().lastElement(), l1, l2 );
		
		if ( askForAngles || askForChannels || askForIllum || askForTimepoints  )
			gd.addMessage( "" );
		
		if ( askForAngles )
			gd.addChoice( query + "_Angles", angleChoice, angleChoice[ defaultAngleChoice ] );
		
		if ( askForChannels )
			gd.addChoice( query + "_Channels", channelChoice, channelChoice[ defaultChannelChoice ] );
		
		if ( askForIllum )
			gd.addChoice( query + "_Illuminations", illumChoice, illumChoice[ defaultIllumChoice ] );
		
		if ( askForTimepoints )
			gd.addChoice( query + "_Timepoints", tpChoice, tpChoice[ defaultTPChoice ] );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;
		
		String xmlFilename = defaultXMLfilename = gd.getNextString();
		
		// try to parse the file anyways
		xmlResult = tryParsing( xmlFilename, true );

		if ( askForAngles )
			xmlResult.angleChoiceIndex = defaultAngleChoice = gd.getNextChoiceIndex();
		else
			xmlResult.angleChoiceIndex = 0; // all timepoints

		if ( askForChannels )
			xmlResult.channelChoiceIndex = defaultChannelChoice = gd.getNextChoiceIndex();
		else
			xmlResult.channelChoiceIndex = 0; // all timepoints

		if ( askForIllum )
			xmlResult.illumChoiceIndex = defaultIllumChoice = gd.getNextChoiceIndex();
		else
			xmlResult.illumChoiceIndex = 0; // all timepoints

		if ( askForTimepoints )
			xmlResult.timepointChoiceIndex = defaultTPChoice = gd.getNextChoiceIndex();
		else
			xmlResult.timepointChoiceIndex = 0; // all timepoints

		// fill up angles, channels, illuminations, timepoints (all, if there is no further dialog)
		if ( !queryDetails( xmlResult ) )
			return null;

		return xmlResult;
	}
	
	/**
	 * Querys a single element from the list
	 * 
	 * @param name - type of elements (e.g. "Timepoint")
	 * @param list - list of available elements
	 * @param defaultSelection - default selection
	 * @return the selection or -1 if cancelled
	 */
	public static int queryIndividualEntry( final String name, final String[] list, int defaultSelection )
	{
		if ( defaultSelection >= list.length )
			defaultSelection = 0;
		
		final GenericDialog gd = new GenericDialog( "Select Single " + name );
		gd.addChoice( "Process", list, list[ defaultSelection ] );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return -1;
		
		return gd.getNextChoiceIndex();
	}
	
	/**
	 * Querys a multiple element from the list
	 * 
	 * @param name - type of elements (e.g. "Timepoints")
	 * @param list - list of available elements
	 * @param defaultSelection - default selection
	 * @return the selection or null if cancelled
	 */
	public static boolean[] queryMultipleEntries( final String name, final String[] list, boolean[] defaultSelection )
	{
		if ( defaultSelection == null || defaultSelection.length != list.length )
		{
			defaultSelection = new boolean[ list.length ];
			defaultSelection[ 0 ] = true;
			for ( int i = 1; i < list.length; ++i )
				defaultSelection[ i ] = false;
			
			// by default select first two
			if ( defaultSelection.length > 1 )
				defaultSelection[ 1 ] = true;
		}
		
		final GenericDialog gd = new GenericDialog( "Select Multiple " + name );
		
		gd.addMessage( "" );
		for ( int i = 0; i < list.length; ++i )
			gd.addCheckbox( list[ i ], defaultSelection[ i ] );
		gd.addMessage( "" );

		GUIHelper.addScrollBars( gd );			
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		for ( int i = 0; i < list.length; ++i )
		{
			if ( gd.getNextBoolean() )
				defaultSelection[ i ] = true;
			else
				defaultSelection[ i ] = false;					
		}

		return defaultSelection;
	}
	
	/**
	 * Querys a pattern of element from the list
	 * 
	 * @param name - type of elements (e.g. "Timepoints")
	 * @param list - list of available elements
	 * @param defaultSelection - default selection (array of size 1 to be able to return it)
	 * @return the selection or null if cancelled
	 */
	public static boolean[] queryPattern( final String name, final String[] list, final String[] defaultSelectionArray )
	{
		String defaultSelection = defaultSelectionArray[ 0 ];

		if ( defaultSelection == null || defaultSelection.length() == 0 )
		{
			defaultSelection = list[ 0 ];
			
			for ( int i = 1; i < Math.min( list.length, 3 ); ++i )
				defaultSelection += "," + list[ i ];
		}
		
		final GenericDialog gd = new GenericDialog( "Select Range of " + name );
		
		gd.addMessage( "" );
		gd.addStringField( "Process_" + name, defaultSelection, 30 );
		gd.addMessage( "" );
		gd.addMessage( "Available " + name + ":" );
		
		final String singular = name.substring( 0, name.length() - 1 ) + " ";
		String allTps = singular + list[ 0 ];
		
		for ( int i = 1; i < list.length; ++i )
			allTps += "\n" + singular + list[ i ];
		
		gd.addMessage( allTps, GUIHelper.smallStatusFont );
		
		GUIHelper.addScrollBars( gd );
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;
		
		// the result
		final boolean[] selected = new boolean[ list.length ];
		
		for ( int i = 0; i < list.length; ++i )
			selected[ i ] = false;
		
		try 
		{
			final ArrayList< Integer > timepointList = IntegerPattern.parseIntegerString( defaultTimePointString = gd.getNextString() );
			
			for ( final int tp : timepointList )
			{
				boolean found = false;
				
				for ( int i = 0; i < list.length && !found; ++i )
				{
					if ( tp == Integer.parseInt( list[ i ] ) )
					{
						selected[ i ] = true;
						found = true;
					}
				}
				
				if ( !found )
					IOFunctions.println( name + " " + tp + " not part of the list of timepoints. Ignoring it." );
			}				
		} 
		catch ( final ParseException e ) 
		{
			IOFunctions.println( "Cannot parse pattern '" + defaultTimePointString + "': " + e );
			return null;
		}
		
		defaultSelectionArray[ 0 ] = defaultSelection;
		
		return selected;
	}
	
	public boolean queryDetails( final XMLParseResult xmlResult )
	{	
		final List< TimePoint > tpList = xmlResult.data.getSequenceDescription().getTimePoints().getTimePointList();
		final List< Angle > angleList = xmlResult.data.getSequenceDescription().getAllAngles();
		final List< Channel > channelList = xmlResult.data.getSequenceDescription().getAllChannels();
		final List< Illumination > illumList = xmlResult.data.getSequenceDescription().getAllIlluminations();
		
		xmlResult.timepoints = new ArrayList< TimePoint >();
		xmlResult.angles = new ArrayList< Angle >();
		xmlResult.channels = new ArrayList< Channel >();
		xmlResult.illums = new ArrayList< Illumination >();
		
		//
		// ANGLES
		//
		if ( xmlResult.angleChoiceIndex == 1 ) // choose a single angle
		{
			final int selection = queryIndividualEntry( "Angles", buildAngleList( angleList, true ), defaultAngleIndex );
			
			if ( selection >= 0 )
				xmlResult.angles.add( angleList.get( defaultAngleIndex = selection ) );
			else
				return false;
		}
		else if ( xmlResult.angleChoiceIndex == 2 || xmlResult.angleChoiceIndex == 3 ) // choose multiple angles or angles defined by pattern
		{
			final boolean[] selection;
			String[] defaultAngle = new String[]{ defaultAngleString };
			
			if ( xmlResult.angleChoiceIndex == 2 )
				selection = queryMultipleEntries( "Angles", buildAngleList( angleList, true ), defaultAngleIndices );
			else
				selection = queryPattern( "Angles", buildAngleList( angleList, false ), defaultAngle );
			
			if ( selection == null )
				return false;
			else
			{
				defaultAngleIndices = selection;
				
				if ( xmlResult.angleChoiceIndex == 3 )
					defaultAngleString = defaultAngle[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						xmlResult.angles.add( angleList.get( i ) );
			}
		}
		else
		{
			for ( int i = 0; i < angleList.size(); ++i )
				xmlResult.angles.add( angleList.get( i ) );				
		}
		
		if ( xmlResult.angles.size() == 0 )
		{
			IOFunctions.println( "List of angles is empty. Stopping." );
			xmlResult.angles = null;
			return false;
		}
		else
		{
			String allAngles = xmlResult.angles.get( 0 ).getName();		
			for ( int i = 1; i < xmlResult.angles.size(); ++i )
				allAngles += "," + xmlResult.angles.get( i ).getName();
			IOFunctions.println( "Angles selected: " + allAngles );
		}

		//
		// CHANNELS
		//
		if ( xmlResult.channelChoiceIndex == 1 ) // choose a single channel
		{
			final int selection = queryIndividualEntry( "Channels", buildChannelList( channelList, true ), defaultChannelIndex );
			
			if ( selection >= 0 )
				xmlResult.channels.add( channelList.get( defaultChannelIndex = selection ) );
			else
				return false;
		}
		else if ( xmlResult.channelChoiceIndex == 2 || xmlResult.channelChoiceIndex == 3 ) // choose multiple channels or channels defined by pattern
		{
			final boolean[] selection;
			String[] defaultChannel = new String[]{ defaultChannelString };
			
			if ( xmlResult.channelChoiceIndex == 2 )
				selection = queryMultipleEntries( "Channels", buildChannelList( channelList, true ), defaultChannelIndices );
			else
				selection = queryPattern( "Channels", buildChannelList( channelList, false ), defaultChannel );
			
			if ( selection == null )
				return false;
			else
			{
				defaultChannelIndices = selection;
				
				if ( xmlResult.channelChoiceIndex == 3 )
					defaultChannelString = defaultChannel[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						xmlResult.channels.add( channelList.get( i ) );
			}
		}
		else
		{
			for ( int i = 0; i < channelList.size(); ++i )
				xmlResult.channels.add( channelList.get( i ) );				
		}
		
		if ( xmlResult.channels.size() == 0 )
		{
			IOFunctions.println( "List of channels is empty. Stopping." );
			xmlResult.channels = null;
			return false;
		}
		else
		{
			String allChannels = xmlResult.channels.get( 0 ).getName();		
			for ( int i = 1; i < xmlResult.channels.size(); ++i )
				allChannels += "," + xmlResult.channels.get( i ).getName();
			IOFunctions.println( "Channels selected: " + allChannels );
		}

		//
		// ILLUMINATION DIRECTIONS
		//
		if ( xmlResult.illumChoiceIndex == 1 ) // choose a single illumination direction
		{
			final int selection = queryIndividualEntry( "Illumination Directions", buildIllumList( illumList, true ), defaultIllumIndex );
			
			if ( selection >= 0 )
				xmlResult.illums.add( illumList.get( defaultIllumIndex = selection ) );
			else
				return false;
		}
		else if ( xmlResult.illumChoiceIndex == 2 || xmlResult.illumChoiceIndex == 3 ) // choose multiple illumination directions or illumination directions defined by pattern
		{
			final boolean[] selection;
			String[] defaultIllum = new String[]{ defaultIllumString };
			
			if ( xmlResult.illumChoiceIndex == 2 )
				selection = queryMultipleEntries( "Illumination Directions", buildIllumList( illumList, true ), defaultIllumIndices );
			else
				selection = queryPattern( "Illumination Directions", buildIllumList( illumList, false ), defaultIllum );
			
			if ( selection == null )
				return false;
			else
			{
				defaultIllumIndices = selection;
				
				if ( xmlResult.illumChoiceIndex == 3 )
					defaultIllumString = defaultIllum[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						xmlResult.illums.add( illumList.get( i ) );
			}
		}
		else
		{
			for ( int i = 0; i < illumList.size(); ++i )
				xmlResult.illums.add( illumList.get( i ) );				
		}
		
		if ( xmlResult.illums.size() == 0 )
		{
			IOFunctions.println( "List of illumination directions is empty. Stopping." );
			xmlResult.illums = null;
			return false;
		}
		else
		{
			String allIllums = xmlResult.illums.get( 0 ).getName();		
			for ( int i = 1; i < xmlResult.illums.size(); ++i )
				allIllums += "," + xmlResult.illums.get( i ).getName();
			IOFunctions.println( "Illumination directions selected: " + allIllums );
		}

		//
		// TIMEPOINTS
		//
		if ( xmlResult.timepointChoiceIndex == 1 ) // choose a single timepoint
		{
			final int selection = queryIndividualEntry( "Timepoint", buildTimepointList( tpList, true ), defaultTimePointIndex );
			
			if ( selection >= 0 )
				xmlResult.timepoints.add( tpList.get( defaultTimePointIndex = selection ) );
			else
				return false;
		}
		else if ( xmlResult.timepointChoiceIndex == 2 || xmlResult.timepointChoiceIndex == 3 ) // choose multiple timepoints or timepoints defined by pattern
		{
			final boolean[] selection;
			String[] defaultTimePoint = new String[]{ defaultTimePointString };
			
			if ( xmlResult.timepointChoiceIndex == 2 )
				selection = queryMultipleEntries( "Timepoints", buildTimepointList( tpList, true ), defaultTimePointIndices );
			else
				selection = queryPattern( "Timepoints", buildTimepointList( tpList, false ), defaultTimePoint );
			
			if ( selection == null )
				return false;
			else
			{
				defaultTimePointIndices = selection;
				
				if ( xmlResult.timepointChoiceIndex == 3 )
					defaultTimePointString = defaultTimePoint[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						xmlResult.timepoints.add( tpList.get( i ) );
			}
		}
		else
		{
			for ( int i = 0; i < tpList.size(); ++i )
				xmlResult.timepoints.add( tpList.get( i ) );				
		}
		
		if ( xmlResult.timepoints.size() == 0 )
		{
			IOFunctions.println( "List of timepoints is empty. Stopping." );
			xmlResult.timepoints = null;
			return false;
		}
		else
		{
			String allTp = xmlResult.timepoints.get( 0 ).getName();		
			for ( int i = 1; i < xmlResult.timepoints.size(); ++i )
				allTp += "," + xmlResult.timepoints.get( i ).getName();
			IOFunctions.println( "Timepoints selected: " + allTp );
		}
		
		return true;
	}
	
	public static String[] buildTimepointList( final List< TimePoint > tpList, final boolean addTitle )
	{
		final String[] timepoints = new String[ tpList.size() ];
		
		for ( int i = 0; i < timepoints.length; ++i )
			if ( addTitle )
				timepoints[ i ] = "Timepoint " + tpList.get( i ).getName();
			else
				timepoints[ i ] = tpList.get( i ).getName();
		
		return timepoints;
	}

	public static String[] buildAngleList( final List< Angle > angleList, final boolean addTitle )
	{
		final String[] angles = new String[ angleList.size() ];
		
		for ( int i = 0; i < angles.length; ++i )
			if ( addTitle )
				angles[ i ] = "Angles " + angleList.get( i ).getName();
			else
				angles[ i ] = angleList.get( i ).getName();
		
		return angles;
	}

	public static String[] buildChannelList( final List< Channel > channelList, final boolean addTitle )
	{
		final String[] channels = new String[ channelList.size() ];
		
		for ( int i = 0; i < channels.length; ++i )
			if ( addTitle )
				channels[ i ] = "Channels " + channelList.get( i ).getName();
			else
				channels[ i ] = channelList.get( i ).getName();
		
		return channels;
	}

	public static String[] buildIllumList( final List< Illumination > illumList, final boolean addTitle )
	{
		final String[] illums = new String[ illumList.size() ];
		
		for ( int i = 0; i < illums.length; ++i )
			if ( addTitle )
				illums[ i ] = "Illumination Directions " + illumList.get( i ).getName();
			else
				illums[ i ] = illumList.get( i ).getName();
		
		return illums;
	}

	public XMLParseResult tryParsing( final String xmlfile, final boolean parseAllTypes )
	{
		final XMLParseResult xml = new XMLParseResult();
		
		xml.xmlfilename = xmlfile;
		xml.message1 = neutralMsg1;
		xml.message2 = noMsg2;
		xml.color = GUIHelper.neutral;
		xml.data = null;
		
		if ( parseAllTypes || ( !parseAllTypes && xmlfile.endsWith( ".xml" ) ) )
		{
			try 
			{
				xml.data = parseXML( xmlfile );
				
				int countMissingViews = 0;
				
				for ( final ViewDescription<?, ?> v : xml.data.getSequenceDescription().getViewDescriptions().values() )
					if ( !v.isPresent() )
						++countMissingViews;
				
				final int angles = xml.data.getSequenceDescription().getAllAngles().size();
				final int channels = xml.data.getSequenceDescription().getAllChannels().size();
				final int illums = xml.data.getSequenceDescription().getAllIlluminations().size();
				final int timepoints = xml.data.getSequenceDescription().numTimePoints();
				
				xml.message1 = goodMsg1;
				xml.message2 = angles + " angles, " + channels + " channels, " + illums + " illumination directions, " + timepoints + " timepoints, " + countMissingViews + " missing views\n" +
						"ImgLoader: " + xml.data.getSequenceDescription().getImgLoader().getClass().getName();
				xml.color = GUIHelper.good;
			}
			catch ( final Exception e )
			{
				xml.message1 = errorMsg1;
				xml.message2 = noMsg2;
				xml.color = GUIHelper.error;

				IOFunctions.println( "Cannot parse '" + xmlfile + "': " + e );
			}
		}
		else if ( xmlfile.length() > 0 )
		{
			xml.message1 = warningMsg1;
			xml.message2 = noMsg2;
			xml.color = GUIHelper.warning;
		}	
		
		return xml;
	}

	public SpimData2 parseXML( final String xmlFilename ) throws JDOMException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final XmlIoSpimData2 io = XmlIo.createDefaultIo();
		return io.load( xmlFilename );
	}
	
	protected void addListeners( final GenericDialog gd, final TextField tf, final Label label1, final Label label2  )
	{
		gd.addDialogListener( new DialogListener()
		{
			@Override
			public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
			{
				if ( e instanceof TextEvent && e.getID() == TextEvent.TEXT_VALUE_CHANGED && e.getSource() == tf )
				{
					final String xmlFilename = tf.getText();
					
					// try parsing if it ends with XML
					final XMLParseResult xmlResult = tryParsing( xmlFilename, false );
					
					label1.setText( xmlResult.message1 );
					label2.setText( xmlResult.message2 );
					label1.setForeground( xmlResult.color );
					label2.setForeground( xmlResult.color );
				}
				return true;
			}
		} );		
	}

	public static void main( String args[] )
	{
		new ImageJ();
		IOFunctions.printIJLog = true;
	
		final LoadParseQueryXML lpq = new LoadParseQueryXML();
		final XMLParseResult xmlResult = lpq.queryXML( true, true, true, true );
		
		for ( final TimePoint i : xmlResult.timepoints )
			System.out.println( i.getId() );
	
	}
}
