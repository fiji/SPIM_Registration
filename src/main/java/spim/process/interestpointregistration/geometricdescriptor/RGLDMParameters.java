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
package spim.process.interestpointregistration.geometricdescriptor;

public class RGLDMParameters
{
	public static float differenceThreshold = 50; 
	public static float ratioOfDistance = 3; 

	public static int numNeighbors = 3;
	public static int redundancy = 1;
	
	protected final float dt, rod;
	protected final int nn, re;
	
	public RGLDMParameters()
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.nn = numNeighbors;
		this.re = redundancy;
	}
	
	public RGLDMParameters( final float differenceThreshold, final float ratioOfDistance, final int numNeighbors, final int redundancy )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.nn = numNeighbors;
		this.re = redundancy;
	}
	
	public float getDifferenceThreshold() { return dt; }
	public float getRatioOfDistance() { return rod; }
	public int getNumNeighbors() { return nn; }
	public int getRedundancy() { return re; }
}
