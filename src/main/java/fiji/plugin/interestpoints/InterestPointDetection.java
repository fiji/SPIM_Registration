package fiji.plugin.interestpoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.models.Point;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import fiji.spimdata.SpimDataBeads;

public interface InterestPointDetection 
{
	public HashMap< ViewId, List< Point > > findInterestPoints( final SpimDataBeads spimData, final ArrayList< Channel> channelsToProcess, final ArrayList< TimePoint > timepointsToProcess );
	
	/**
	 * @param spimData
	 * @param channelsToProcess - which channels to segment in
	 * @param timepointsToProcess - which timepoints were selected
	 * @return
	 */
	public boolean queryParameters( final SpimDataBeads spimData, final ArrayList< Channel> channelsToProcess, final ArrayList< TimePoint > timepointsToProcess );
	
	/**
	 * @return - a new instance without any special properties
	 */
	public InterestPointDetection newInstance();
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public String getDescription();
}
