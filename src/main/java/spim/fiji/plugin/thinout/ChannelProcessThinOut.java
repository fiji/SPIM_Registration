/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
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
