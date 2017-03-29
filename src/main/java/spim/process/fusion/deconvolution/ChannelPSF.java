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
package spim.process.fusion.deconvolution;

import mpicbg.spim.data.sequence.Channel;
import net.imglib2.type.numeric.real.FloatType;

public class ChannelPSF
{
	final Channel channel;
	final String label;
	final Channel otherChannel;
	ExtractPSF< FloatType > extractPSF;
	
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

	public ChannelPSF( final Channel channel )
	{
		this.channel = channel;
		this.label = null;
		this.otherChannel = null;
	}

	public boolean isExtractedPSF() { return label != null;	}
	public Channel getChannel() { return channel; }
	public String getLabel() { return label; }
	public Channel getOtherChannel() { return otherChannel; }
	
	public void setExtractPSFInstance( final ExtractPSF< FloatType > e ) { this.extractPSF = e; }
	public ExtractPSF< FloatType > getExtractPSFInstance(){ return extractPSF; }
}
