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
package spim.process.interestpointregistration.geometrichashing;

public class GeometricHashingParameters
{
	public static float differenceThreshold = 50;
	public static float ratioOfDistance = 10;
	public static boolean useAssociatedBeads = false;

	protected final float dt, rod;
	protected final boolean ub;
	
	public GeometricHashingParameters()
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.ub = useAssociatedBeads;
	}
	
	public GeometricHashingParameters( final float differenceThreshold, final float ratioOfDistance, final boolean useAssociatedBeads )
	{
		this.dt = differenceThreshold;
		this.rod = ratioOfDistance;
		this.ub = useAssociatedBeads;
	}
	
	public float getDifferenceThreshold() { return dt; }
	public float getRatioOfDistance() { return rod; }
	public boolean getUseAssociatedBeads() { return ub; }
}
