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
package spim.process.interestpointregistration;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class RANSACParameters
{
    public static float max_epsilon = 5;
    public static float min_inlier_ratio = 0.1f;
    public static int num_iterations = 1000;
    public static float min_inlier_factor = 3f;

    final protected float maxEpsilon, minInlierRatio, minInlierFactor;
    final protected int numIterations;
    
    public RANSACParameters( final float maxEpsilon, final float minInlierRatio, final float minInlierFactor, final int numIterations )
    {
    	this.maxEpsilon = maxEpsilon;
    	this.minInlierRatio = minInlierRatio;
    	this.minInlierFactor = minInlierFactor;
    	this.numIterations = numIterations;
    }
    
    public RANSACParameters()
    {
    	this.maxEpsilon = max_epsilon;
    	this.numIterations = num_iterations;
    	this.minInlierRatio = min_inlier_ratio;
    	this.minInlierFactor = min_inlier_factor;
    }
    
    public float getMaxEpsilon() { return maxEpsilon; }
    public float getMinInlierRatio() { return minInlierRatio; }
    public float getMinInlierFactor() { return minInlierFactor; }
    public int getNumIterations() { return numIterations; }
}
