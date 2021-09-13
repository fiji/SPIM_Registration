/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2021 Fiji developers.
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
package mpicbg.spim.registration.threshold;

import spim.vecmath.Point3d;

public class ComponentProperties 
{
	// the label this component has
	int label;
	
	// size in pixels
	public int size;	
	
	// dimension in pixels
	public int minX = Integer.MAX_VALUE;
	public int maxX = Integer.MIN_VALUE;
	public int minY = Integer.MAX_VALUE;
	public int maxY = Integer.MIN_VALUE;
	public int minZ = Integer.MAX_VALUE;
	public int maxZ = Integer.MIN_VALUE;		
	public int sizeX, sizeY, sizeZ;
	
	// center of mass
	public Point3d center;
}
