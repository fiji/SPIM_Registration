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
package mpicbg.spim.fusion;

import java.util.ArrayList;

import mpicbg.spim.io.SPIMConfiguration;
import mpicbg.spim.registration.ViewDataBeads;

public abstract class CombinedPixelWeightener<I>
{
	final ArrayList<ViewDataBeads> views;
	final SPIMConfiguration conf;
	
	protected CombinedPixelWeightener( final ArrayList<ViewDataBeads> views )
	{
		this.views = views;
		this.conf = views.get( 0 ).getViewStructure().getSPIMConfiguration();
	}
	
	/**
	 * Updates the weights for all images, knowing where to grab all pixels from in each source image
	 * and which of the images are hit
	 * 
	 * @param locations - the locations of the source pixel in each source image 
	 * @param use - if the particular view is hit or not
	 */
	public abstract void updateWeights( final int[][] locations, final boolean[] use );

	public abstract void updateWeights( final double[][] locations, final boolean[] use );

	/**
	 * Returns the weightening factor for one view
	 * 
	 * @param view - which source image
	 * @return a weightening factor between 0 and 1
	 */
	public abstract double getWeight( final int view );
	
	/**
	 * Closes the created images if applicable
	 */
	public abstract void close();
}
