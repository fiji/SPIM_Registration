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
package spim.fiji.plugin.interestpointdetection;

import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.registration.bead.SegmentationBenchmark;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public abstract class InterestPointDetection 
{
	/**
	 * which viewIds to process, set in queryParameters
	 */
	final List< ViewId > viewIdsToProcess;

	final SpimData2 spimData;
	final public SegmentationBenchmark benchmark = new SegmentationBenchmark();

	/**
	 * @param spimData
	 * @param viewIdsToProcess - which view id's to segment
	 */
	public InterestPointDetection(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess )
	{
		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
	}

	public List< ViewId > getViewIdsToProcess() { return viewIdsToProcess; }
	public SegmentationBenchmark getBenchmark() { return benchmark; }
	
	/**
	 * Perform the interestpoint detection for one timepoint
	 * 
	 * @return
	 */
	public abstract HashMap< ViewId, List< InterestPoint > > findInterestPoints( final TimePoint tp );
	
	/**
	 * Query the necessary parameters for the interestpoint detection
	 * 
	 * @param downsample - whether to downsample the images before performing the blob detection
	 * @param defineAnisotropy - whether to use/query for anisotropy in resolution of the data
	 * @param additionalSmoothing - smooth in certain dimensions before computing the segmentation
	 * @param setMinMax - whether to define minimal and maximal intensity relative to whom everything is normalized to [0...1]
	 * @return
	 */
	public abstract boolean queryParameters(
			final boolean downsample,
			final boolean defineAnisotropy,
			final boolean additionalSmoothing,
			final boolean setMinMax,
			final boolean limitDetections );
	
	/**
	 * @param spimData
	 * @param viewIdsToProcess - which view id's to segment
	 * @return - a new instance without any special properties
	 */
	public abstract InterestPointDetection newInstance(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();
	
	/**
	 * @return - stored in the XML so that it is reproducible how the points were segmented
	 */
	public abstract String getParameters( final int channelId );
}
