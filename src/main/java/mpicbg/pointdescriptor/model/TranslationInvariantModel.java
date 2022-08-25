/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2022 Fiji developers.
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
package mpicbg.pointdescriptor.model;

import mpicbg.models.AbstractModel;
import mpicbg.models.Model;
import mpicbg.pointdescriptor.AbstractPointDescriptor;

/**
 * This class is a subtle hint that {@link Model}s which are used to fit {@link AbstractPointDescriptor}s should be translation invariant. 
 * 
 * @author Stephan Preibisch (preibisch@mpi-cbg.de)
 *
 * @param <M> something that extends {@link Model}
 */
public abstract class TranslationInvariantModel< M extends TranslationInvariantModel< M > > extends AbstractModel< M >
{
	/**
	 * The {@link TranslationInvariantModel} can tell which dimensions it supports.
	 * 
	 * @param numDimensions - The dimensionality (e.g. 3 means 3-dimensional) 
	 * @return - If the {@link TranslationInvariantModel} supports that dimensionality
	 */
	public abstract boolean canDoNumDimension( int numDimensions );
}
