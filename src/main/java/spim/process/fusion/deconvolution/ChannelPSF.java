package spim.process.fusion.deconvolution;

import mpicbg.spim.data.sequence.Channel;

public class ChannelPSF
{
	final Channel channel;
	final String label;
	final Channel otherChannel;
	
	public ChannelPSF( final Channel channel, final String label )
	{
		this.channel = channel;
		this.label = label;
		this.otherChannel = null;
	}
	
	public ChannelPSF( final Channel channel, final Channel otherPSF )
	{
		this.channel = channel;
		this.label = null;
		this.otherChannel = otherPSF;
	}
	
	public Channel getChannel() { return channel; }
	public String getLabel() { return label; }
	public Channel getOtherChannel() { return otherChannel; }
}
