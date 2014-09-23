package spim.process.interestpointregistration;

import java.util.List;

import spim.fiji.spimdata.interestpoints.InterestPoint;

/**
 * The interestpoint list from a certain channel;
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class MatchPointList
{
	final List< InterestPoint > interestpointList;
	final ChannelProcess channel;
	
	public MatchPointList( final List< InterestPoint > interestpointList, final ChannelProcess channel )
	{
		this.interestpointList = interestpointList;
		this.channel = channel;
	}
	
	public List< InterestPoint > getInterestpointList() { return interestpointList; }
	public ChannelProcess getChannelProcessed() { return channel; }
}
