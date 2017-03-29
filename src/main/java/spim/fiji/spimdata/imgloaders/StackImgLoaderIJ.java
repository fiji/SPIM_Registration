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
package spim.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;

public class StackImgLoaderIJ extends StackImgLoader< LegacyStackImgLoaderIJ >
{
	public StackImgLoaderIJ(
			final File path, final String fileNamePattern, final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final int layoutTP, final int layoutChannels, final int layoutIllum, final int layoutAngles,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		super( new LegacyStackImgLoaderIJ( path, fileNamePattern, imgFactory, layoutTP, layoutChannels, layoutIllum, layoutAngles, sequenceDescription ) );
	}
}
