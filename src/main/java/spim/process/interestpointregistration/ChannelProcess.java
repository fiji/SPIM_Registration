package spim.process.interestpointregistration;

import mpicbg.spim.data.sequence.Channel;

public class ChannelProcess
{
	protected Channel channel;
	protected String label;
	
	public ChannelProcess( final Channel channel, final String label )
	{
		this.channel = channel;
		this.label = label;
	}
	
	public Channel getChannel() { return channel; }
	public String getLabel() { return label; }
}
