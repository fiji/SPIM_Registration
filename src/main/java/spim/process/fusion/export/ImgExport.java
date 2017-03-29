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

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.boundingbox.BoundingBoxGUI;

public interface ImgExport
{
	/**
	 * Called last when the fusion is finished (e.g. to write the XML)
	 *
	 * @return - true if the spimdata was modified, otherwise false
	 */
	public boolean finish();

	public void setXMLData( final List< TimePoint > timepointsToProcess, final List< ViewSetup > newViewSetups );

	/**
	 * Exports the image (min and max intensity will be computed)
	 * 
	 * @param img - Note, in rare cases this can be null (i.e. do nothing)
	 * @param bb - the bounding box used to fuse this image
	 * @param tp - the current (new) timepoint
	 * @param vs - the current (new) viewsetup
	 */
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval< T > img, final BoundingBoxGUI bb, final TimePoint tp, final ViewSetup vs );
	
	/**
	 * Exports the image using a predefined min/max
	 * 
	 * @param img - Note, in rare cases this can be null (i.e. do nothing)
	 * @param bb - the bounding box used to fuse this image
	 * @param tp - the current (new) timepoint
	 * @param vs - the current (new) viewsetup
	 * @param min - define min intensity of this image
	 * @param max - define max intensity of this image
	 */
	public < T extends RealType< T > & NativeType< T > > boolean exportImage( final RandomAccessibleInterval< T > img, final BoundingBoxGUI bb, final TimePoint tp, final ViewSetup vs, final double min, final double max );
	
	/**
	 * Query the necessary parameters for the fusion (new dialog has to be made)
	 * 
	 * @return
	 */
	public abstract boolean queryParameters( final SpimData2 spimData, final boolean is16bit );
	
	/**
	 * Query additional parameters within the bounding box dialog
	 */
	public abstract void queryAdditionalParameters( final GenericDialog gd, final SpimData2 spimData );

	/**
	 * Parse the additional parameters added before within the bounding box dialog
	 * @param gd
	 * @return
	 */
	public abstract boolean parseAdditionalParameters( final GenericDialog gd, final SpimData2 spimData );
	
	public abstract ImgExport newInstance();
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();
}
