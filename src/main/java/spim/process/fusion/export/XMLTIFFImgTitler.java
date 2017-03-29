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
package spim.process.fusion.export;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;

public class XMLTIFFImgTitler implements ImgTitler
{
	final List< TimePoint > timepoints;
	final List< ViewSetup > viewSetups;
	
	public XMLTIFFImgTitler( final List< TimePoint > newTimepoints, final List< ViewSetup > newViewSetups )
	{
		this.timepoints = newTimepoints;
		this.viewSetups = newViewSetups;
	}
	
	@Override
	public String getImageTitle( final TimePoint tp, final ViewSetup vs )
	{
		String filename = "img";

		if ( timepoints.size() > 1 )
			filename += "_TL" + tp.getId();
		
		if ( getAllChannels( viewSetups ).size() > 1 )
			filename += "_Ch" + vs.getChannel().getName();
		
		if ( getAllIlluminations( viewSetups ).size() > 1 )
			filename += "_Ill" + vs.getIllumination().getName();
		
		if ( getAllAngles( viewSetups ).size() > 1 )
			filename += "_Angle" + vs.getAngle().getName();

		return filename;
	}

	public static Set< Angle > getAllAngles( final List< ViewSetup > viewSetups )
	{
		final HashSet< Angle > angles = new HashSet< Angle >();
		
		for ( final ViewSetup v : viewSetups )
			angles.add( v.getAngle() );
		
		return angles;
	}

	public static Set< Channel > getAllChannels( final List< ViewSetup > viewSetups )
	{
		final HashSet< Channel > channels = new HashSet< Channel >();
		
		for ( final ViewSetup v : viewSetups )
			channels.add( v.getChannel() );
		
		return channels;
	}

	public static Set< Illumination > getAllIlluminations( final List< ViewSetup > viewSetups )
	{
		final HashSet< Illumination > illums = new HashSet< Illumination >();
		
		for ( final ViewSetup v : viewSetups )
			illums.add( v.getIllumination() );
		
		return illums;
	}

}
