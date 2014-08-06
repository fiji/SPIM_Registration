package spim.fiji.plugin.thinout;

import mpicbg.spim.data.sequence.Channel;
import spim.process.interestpointregistration.ChannelProcess;

public class ChannelProcessThinOut extends ChannelProcess
{
	final boolean showHistogram;
	final int subsampling;
	final String newLabel;
	double min, max;
	boolean keepRange;

	public ChannelProcessThinOut( final Channel channel, final String label, final String newLabel, final boolean showHistogram, final int subsampling )
	{
		super( channel, label );
		this.newLabel = newLabel;
		this.showHistogram = showHistogram;
		this.subsampling = subsampling;
	}

	public boolean showHistogram() { return showHistogram; }
	public int getSubsampling() { return subsampling; }
	public String getNewLabel() { return newLabel; }
	public double getMin() { return min; }
	public double getMax() { return max; }
	public boolean keepRange() { return keepRange; }
	
	public void setMin( final double min ) { this.min = min; }
	public void setMax( final double max ) { this.max = max; }
	public void setKeepRange( final boolean keep ) { this.keepRange = keep; }
}
