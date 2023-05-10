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
package spim.process.interestpointregistration.geometrichashing;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Callable;

import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.util.Pair;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.Detection;
import spim.process.interestpointregistration.PairwiseMatch;
import spim.process.interestpointregistration.RANSAC;
import spim.process.interestpointregistration.RANSACParameters;
import spim.process.interestpointregistration.TransformationModel;

public class GeometricHashingPairwise implements Callable< PairwiseMatch >
{
	final PairwiseMatch pair;
	final TransformationModel model;
	final RANSACParameters rp;
	final GeometricHashingParameters gp;
	final String comparison;
	
	public GeometricHashingPairwise( final PairwiseMatch pair, final TransformationModel model, final String comparison, final RANSACParameters rp, final GeometricHashingParameters gp )
	{ 
		this.pair = pair;
		this.rp = rp;
		this.gp = gp;
		this.model = model;
		this.comparison = comparison;
	}

	public GeometricHashingPairwise( final PairwiseMatch pair, final TransformationModel model, final String comparison, final RANSACParameters rp )
	{
		this( pair, model, comparison, rp, new GeometricHashingParameters() );
	}

	public GeometricHashingPairwise( final PairwiseMatch pair, final TransformationModel model, final String comparison )
	{
		this( pair, model, comparison, new RANSACParameters(), new GeometricHashingParameters() );
	}
	
	@Override
	public PairwiseMatch call()
	{
		final GeometricHasher hasher = new GeometricHasher();
		
		final ArrayList< Detection > listA = new ArrayList< Detection >();
		final ArrayList< Detection > listB = new ArrayList< Detection >();
		
		for ( final InterestPoint i : pair.getListA() )
			listA.add( new Detection( i.getId(), i.getL() ) );

		for ( final InterestPoint i : pair.getListB() )
			listB.add( new Detection( i.getId(), i.getL() ) );

		if ( listA.size() < 4 || listB.size() < 4 )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): " + comparison + ": "
					+ "Not enough detections to match (4 required per list, |listA|= " + listA.size() + ", |listB|= " + listB.size() + ")" );
			pair.setCandidates( new ArrayList< PointMatchGeneric< Detection > >() );
			pair.setInliers( new ArrayList<PointMatchGeneric< Detection > >(), Double.NaN );
			return pair;
		}

		final ArrayList< PointMatchGeneric< Detection > > candidates = hasher.extractCorrespondenceCandidates( 
				listA,
				listB,
				gp.getDifferenceThreshold(), 
				gp.getRatioOfDistance(), 
				gp.getUseAssociatedBeads() );

		pair.setCandidates( candidates );

		// compute ransac and remove inconsistent candidates
		final ArrayList< PointMatchGeneric< Detection > > inliers = new ArrayList< PointMatchGeneric< Detection > >();

		final Pair< String, Double > result = RANSAC.computeRANSAC( candidates, inliers, this.model.getModel(), rp.getMaxEpsilon(), rp.getMinInlierRatio(), rp.getMinInlierFactor(), rp.getNumIterations() );

		pair.setInliers( inliers, result.getB() );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): " + comparison + ": " + result.getA() );

		return pair;
	}
}
