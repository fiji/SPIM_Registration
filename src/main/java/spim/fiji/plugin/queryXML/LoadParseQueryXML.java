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

public class LoadParseQueryXML extends GenericLoadParseQueryXML< SpimData2, SequenceDescription, ViewSetup, ViewDescription, ImgLoader< ? >, XmlIoSpimData2 >
{
	public LoadParseQueryXML() { super( new XmlIoSpimData2( "" ) ); }

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

		if ( askForTimepoints )
			specifyAttributes.add( "Timepoint" );

		if ( askForChannels )
			specifyAttributes.add( "channel" );

		if ( askForAngles )
			specifyAttributes.add( "angle" );

		if ( askForIllum )
			specifyAttributes.add( "illumination" );

		return queryXML( additionalTitle, query, specifyAttributes );
	}

	@Override
	public boolean queryXML(
			final String additionalTitle,
			String query,
			List< String > specifyAttributes )
	{
		boolean success = super.queryXML( additionalTitle, query, specifyAttributes );

		// make sure the internal IO is updated to reflect the cluster saving
		if ( success )
			this.getIO().setClusterExt( this.getClusterExtension() );

		return success;
	}

	public boolean loadXML()
	{
		boolean success = super.loadXML( null );

		if ( !queryDetails() )
			return false;

		// make sure the internal IO is updated to reflect the cluster saving
		if ( success )
			this.getIO().setClusterExt( this.getClusterExtension() );

		return success;
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

	//@Override
	//public XmlIoSpimData2 getIO() { return (XmlIoSpimData2)io; }

	public static void main( String args[] )
	{
		new ImageJ();
		IOFunctions.printIJLog = true;
	
		final LoadParseQueryXML lpq = new LoadParseQueryXML();
		
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
