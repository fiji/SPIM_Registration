/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2023 Fiji developers.
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
package mpicbg.pointdescriptor.matcher;

import java.util.ArrayList;

import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.pointdescriptor.AbstractPointDescriptor;
import mpicbg.pointdescriptor.LinkedPoint;

public interface Matcher
{
	/**
	 * @return An {@link ArrayList} of corresponding set of {@link PointMatch}es which contain {@link LinkedPoint}s linking to the actual {@link Point} instance they are created from
	 */
	public ArrayList<ArrayList<PointMatch>> createCandidates( AbstractPointDescriptor<?, ?> pd1, AbstractPointDescriptor<?, ?> pd2 );
	
	/**
	 * Computes a normalization factor for the case that the different set of {@link PointMatch}es are not comparable 
	 * (for example number of neighbors used is not constant)
	 * 
	 * @param matches the set of {@link PointMatch}es
	 * @return The normalization factor for a certain set of {@link PointMatch}es 
	 */
	public double getNormalizationFactor( final ArrayList<PointMatch> matches, Object fitResult );
	
	/**
	 * @return The number of nearest neighbors required for this {@link Matcher} 
	 */
	public int getRequiredNumNeighbors();
}
