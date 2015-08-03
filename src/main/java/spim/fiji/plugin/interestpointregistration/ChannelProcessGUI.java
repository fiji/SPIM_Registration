package spim.fiji.plugin.interestpointregistration;

import mpicbg.spim.data.sequence.Channel;

public class ChannelProcessGUI
{
	protected Channel channel;
	protected String label;
	
	public ChannelProcessGUI( final Channel channel, final String label )
	{
		this.channel = channel;
		this.label = label;
	}
	
	public Channel getChannel() { return channel; }
	public String getLabel() { return label; }
}
