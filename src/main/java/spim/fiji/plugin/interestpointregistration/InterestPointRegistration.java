package spim.fiji.plugin.interestpointregistration;

import java.util.ArrayList;

import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.spimdata.SpimData2;
import ij.gui.GenericDialog;

public abstract class InterestPointRegistration
{
	final SpimData2 spimData;
	final ArrayList< TimePoint > timepointsToProcess; 
	final ArrayList< ChannelProcess > channelsToProcess;
	
	public InterestPointRegistration( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		this.spimData = spimData;
		this.timepointsToProcess = timepointsToProcess;
		this.channelsToProcess = channelsToProcess;
	}
	
	/**
	 * Creates lists of input points for the registration, depending if the input is the current transformation or just the calibration
	 * 
	 * @param inputTransform ( 0 == calibration, 1 == current transform, including calibration )
	 */
	public void prepareInputPoints( final int inputTransform )
	{
		final ViewRegistrations registrations = spimData.getViewRegistrations();
	}

	/**
	 * adds the questions this registration wants to ask
	 * 
	 * @param gd
	 * @param isTimeSeriesRegistration
	 */
	public abstract void addQuery( final GenericDialog gd, final boolean isTimeSeriesRegistration );
	
	/**
	 * queries the questions asked before
	 * 
	 * @param gd
	 * @param isTimeSeriesRegistration
	 * @return
	 */
	public abstract boolean parseDialog( final GenericDialog gd, final boolean isTimeSeriesRegistration );
	
	/**
	 * @return - a new instance without any special properties
	 */
	public abstract InterestPointRegistration newInstance( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();
}
