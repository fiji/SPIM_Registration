package spim.fiji.plugin.queryXML;

import ij.ImageJ;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;

public class LoadParseQueryXML2 extends GenericLoadParseQueryXML< SpimData2, SequenceDescription, ViewSetup, ViewDescription, ImgLoader< ? > >
{
	static { GenericLoadParseQueryXML.defaultXMLfilename = "/Users/preibischs/dataset.xml"; }
	
	public LoadParseQueryXML2() { super( new XmlIoSpimData2() ); }

	public boolean queryXML(
			final String additionalTitle,
			final boolean askForAngles,
			final boolean askForChannels,
			final boolean askForIllum,
			final boolean askForTimepoints )
	{
		return queryXML( additionalTitle, "Process", askForAngles, askForChannels, askForIllum, askForTimepoints );
	}

	public boolean queryXML(
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
	public boolean queryXML(
			final String additionalTitle,
			String query,
			final boolean askForAngles,
			final boolean askForChannels,
			final boolean askForIllum,
			final boolean askForTimepoints )
	{
		final ArrayList< String > specifyAttributes = new ArrayList< String >();
		specifyAttributes.add( "Timepoint" );
		specifyAttributes.add( "channel" );
		specifyAttributes.add( "angle" );
		specifyAttributes.add( "illumination" );
		
		return queryXML( additionalTitle, query, specifyAttributes );
	}
	/**
	 * @return All angles that should be processed
	 */
	@SuppressWarnings("unchecked")
	public List< Angle > getAnglesToProcess() { return (List< Angle >)(Object)attributeInstancesToProcess.get( "angle" ); }

	/**
	 * @return All channels that should be processed
	 */
	@SuppressWarnings("unchecked")
	public List< Channel > getChannelsToProcess() { return (List< Channel >)(Object)attributeInstancesToProcess.get( "channel" ); }

	/**
	 * @return All illumination directions that should be processed
	 */
	@SuppressWarnings("unchecked")
	public List< Illumination > getIlluminationsToProcess() { return (List< Illumination >)(Object)attributeInstancesToProcess.get( "illumination" ); }

	public static void main( String args[] )
	{
		new ImageJ();
		IOFunctions.printIJLog = true;
	
		final LoadParseQueryXML2 lpq = new LoadParseQueryXML2();
		
		final ArrayList< String > queryFor = new ArrayList< String >();
		queryFor.add( "Timepoint" );
		queryFor.add( "channel" );
		queryFor.add( "angle" );
		queryFor.add( "illumination" );
		
		lpq.queryXML( true, true, true, true );
		
		for ( final TimePoint i : lpq.getTimePointsToProcess() )
			System.out.println( i.getId() );
	
		for ( final ViewSetup v : lpq.getViewSetupsToProcess() )
		{
			System.out.println( v.getId() + " " + v.getAngle().getName() + " " + v.getChannel().getName() + " " + v.getIllumination().getName() );
		}
	}
}
