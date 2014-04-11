package spim.fiji.plugin.fusion;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.spimdata.SpimData2;

public abstract class Fusion
{
	/**
	 * which angles to process, set in queryParameters
	 */
	final ArrayList< Angle > anglesToProcess;

	/**
	 * which channels to process, set in queryParameters
	 */
	final ArrayList< Channel> channelsToProcess;

	/**
	 * which illumination directions to process, set in queryParameters
	 */
	final ArrayList< Illumination > illumsToProcess;

	/**
	 * which timepoints to process, set in queryParameters
	 */
	final ArrayList< TimePoint > timepointsToProcess;

	final SpimData2 spimData;

	/**
	 * @param spimData
	 * @param anglesToPrcoess - which angles to segment
	 * @param channelsToProcess - which channels to segment in
	 * @param illumsToProcess - which illumination directions to segment
	 * @param timepointsToProcess - which timepoints were selected
	 */
	public Fusion(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< Channel> channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		this.spimData = spimData;
		this.anglesToProcess = anglesToProcess;
		this.channelsToProcess = channelsToProcess;
		this.illumsToProcess = illumsToProcess;
		this.timepointsToProcess = timepointsToProcess;
	}
	
	/**
	 * Query the necessary parameters for the fusion
	 * 
	 * @return
	 */
	public abstract boolean queryParameters();
	
	/**
	 * @param spimData
	 * @param anglesToPrcoess - which angles to segment
	 * @param channelsToProcess - which channels to segment in
	 * @param illumsToProcess - which illumination directions to segment
	 * @param timepointsToProcess - which timepoints were selected
	 * @return - a new instance without any special properties
	 */
	public abstract Fusion newInstance(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();	
}
