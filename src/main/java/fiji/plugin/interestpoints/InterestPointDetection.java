package fiji.plugin.interestpoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.models.Point;
import mpicbg.spim.data.sequence.ViewId;
import fiji.spimdata.SpimDataBeads;

public interface InterestPointDetection 
{
	public HashMap< ViewId, List< Point > > findInterestPoints( final SpimDataBeads spimData, final boolean[] channelIds, final ArrayList< Integer > timepointindices );
	
	/**
	 * @param spimData
	 * @param channelIds - which of the channel id's to segment in
	 * @param timepointindices - which timepoint id's were selected
	 * @return
	 */
	public boolean queryParameters( final SpimDataBeads spimData, final boolean[] channelIds, final ArrayList< Integer > timepointindices );
	
	/**
	 * @return - a new instance without any special properties
	 */
	public InterestPointDetection newInstance();
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public String getDescription();
}
