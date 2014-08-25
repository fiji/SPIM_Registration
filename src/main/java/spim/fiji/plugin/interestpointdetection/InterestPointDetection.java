package spim.fiji.plugin.interestpointdetection;

import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.registration.bead.SegmentationBenchmark;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public abstract class InterestPointDetection 
{
	/**
	 * which angles to process, set in queryParameters
	 */
	final List< Angle > anglesToProcess;

	/**
	 * which channels to process, set in queryParameters
	 */
	final List< Channel> channelsToProcess;

	/**
	 * which illumination directions to process, set in queryParameters
	 */
	final List< Illumination > illumsToProcess;

	/**
	 * which timepoints to process, set in queryParameters
	 */
	final List< TimePoint > timepointsToProcess;

	final SpimData2 spimData;
	final public SegmentationBenchmark benchmark = new SegmentationBenchmark();

	/**
	 * @param spimData
	 * @param anglesToPrcoess - which angles to segment
	 * @param channelsToProcess - which channels to segment in
	 * @param illumsToProcess - which illumination directions to segment
	 * @param timepointsToProcess - which timepoints were selected
	 */
	public InterestPointDetection(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< Channel> channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
	{
		this.spimData = spimData;
		this.anglesToProcess = anglesToProcess;
		this.channelsToProcess = channelsToProcess;
		this.illumsToProcess = illumsToProcess;
		this.timepointsToProcess = timepointsToProcess;
	}

	public List< Angle > getAnglesToProcess() { return anglesToProcess; }
	public List< Channel > getChannelsToProcess() { return channelsToProcess; }
	public List< Illumination > getIllumsToProcess() { return illumsToProcess; }
	public List< TimePoint > getTimepointsToProcess() { return timepointsToProcess; }
	public SegmentationBenchmark getBenchmark() { return benchmark; }
	
	/**
	 * Perform the interestpoint detection for one timepoint
	 * 
	 * @return
	 */
	public abstract HashMap< ViewId, List< InterestPoint > > findInterestPoints( final TimePoint tp );
	
	/**
	 * Query the necessary parameters for the interestpoint detection
	 * 
	 * @param downsample - whether to downsample the images before performing the blob detection
	 * @param defineAnisotropy - whether to use/query for anisotropy in resolution of the data
	 * @param additionalSmoothing - smooth in certain dimensions before computing the segmentation
	 * @param setMinMax - whether to define minimal and maximal intensity relative to whom everything is normalized to [0...1]
	 * @return
	 */
	public abstract boolean queryParameters(
			final boolean downsample,
			final boolean defineAnisotropy,
			final boolean additionalSmoothing,
			final boolean setMinMax );
	
	/**
	 * @param spimData
	 * @param anglesToPrcoess - which angles to segment
	 * @param channelsToProcess - which channels to segment in
	 * @param illumsToProcess - which illumination directions to segment
	 * @param timepointsToProcess - which timepoints were selected
	 * @return - a new instance without any special properties
	 */
	public abstract InterestPointDetection newInstance(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();
	
	/**
	 * @return - stored in the XML so that it is reproducible how the points were segmented
	 */
	public abstract String getParameters( final int channelId );
}
