package spim.fiji.plugin.interestpoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public interface InterestPointDetection 
{
	public HashMap< ViewId, List< InterestPoint > > findInterestPoints( final SpimData2 spimData, final ArrayList< Channel> channelsToProcess, final ArrayList< TimePoint > timepointsToProcess );
	
	/**
	 * @param spimData
	 * @param channelsToProcess - which channels to segment in
	 * @param timepointsToProcess - which timepoints were selected
	 * @return
	 */
	public boolean queryParameters( final SpimData2 spimData, final ArrayList< Channel> channelsToProcess, final ArrayList< TimePoint > timepointsToProcess );
	
	/**
	 * @return - a new instance without any special properties
	 */
	public InterestPointDetection newInstance();
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public String getDescription();
	
	/**
	 * @return - stored in the XML so that it is reproducible how the points were segmented
	 */
	public String getParameters( final int channelId );
}
