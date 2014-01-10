package spim.fiji.plugin.interestpointregistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import ij.gui.GenericDialog;

public abstract class InterestPointRegistration
{
	final SpimData2 spimData;
	final ArrayList< TimePoint > timepointsToProcess; 
	final ArrayList< ChannelProcess > channelsToProcess;
	
	/*
	 * type of input transform ( 0 == calibration, 1 == current transform, including calibration )
	 */
	int inputTransform = 0;
	
	public InterestPointRegistration( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		this.spimData = spimData;
		this.timepointsToProcess = timepointsToProcess;
		this.channelsToProcess = channelsToProcess;
	}
	
	/**
	 * @param inputTransform type of input transform ( 0 == calibration, 1 == current transform, including calibration )
	 */
	public void setInitialTransformType( final int inputTransform ) { this.inputTransform = inputTransform; }
	
	/**
	 * Creates lists of input points for the registration, depending if the input is the current transformation or just the calibration
	 * 
	 * @param timepoint
	 */
	protected HashMap< ViewId, List< InterestPoint > > getInterestPoints( final TimePoint timepoint )
	{
		final HashMap< ViewId, List< InterestPoint > > interestPoints = new HashMap< ViewId, List< InterestPoint > >();
		final ViewRegistrations registrations = spimData.getViewRegistrations();

		return null;
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
