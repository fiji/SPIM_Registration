package spim.process.interestpointregistration.pairwise;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.headless.registration.RANSACParameters;
import spim.headless.registration.geometrichashing.GeometricHashingParameters;
import spim.process.interestpointregistration.Detection;
import spim.process.interestpointregistration.RANSAC;

public class GeometricHashingPairwise implements MatcherPairwise
{
	final RANSACParameters rp;
	final GeometricHashingParameters gp;

	public GeometricHashingPairwise(
			final RANSACParameters rp,
			final GeometricHashingParameters gp )
	{ 
		this.rp = rp;
		this.gp = gp;
	}

	@Override
	public PairwiseResult match( final List< InterestPoint > listAIn, final List< InterestPoint > listBIn )
	{
		final PairwiseResult result = new PairwiseResult();
		final GeometricHasher hasher = new GeometricHasher();
		
		final ArrayList< Detection > listA = new ArrayList< Detection >();
		final ArrayList< Detection > listB = new ArrayList< Detection >();

		for ( final InterestPoint i : listAIn )
			listA.add( new Detection( i.getId(), i.getL() ) );

		for ( final InterestPoint i : listBIn )
			listB.add( new Detection( i.getId(), i.getL() ) );

		if ( listA.size() < 4 || listB.size() < 4 )
		{
			result.setResult( System.currentTimeMillis(), "Not enough detections to match" );
			result.setCandidates( new ArrayList< PointMatchGeneric< Detection > >() );
			result.setInliers( new ArrayList<PointMatchGeneric< Detection > >(), Double.NaN );
			return result;
		}

		final ArrayList< PointMatchGeneric< Detection > > candidates = hasher.extractCorrespondenceCandidates( 
				listA,
				listB,
				gp.getDifferenceThreshold(), 
				gp.getRatioOfDistance(), 
				gp.getUseAssociatedBeads() );

		result.setCandidates( candidates );

		// compute ransac and remove inconsistent candidates
		final ArrayList< PointMatchGeneric< Detection > > inliers = new ArrayList< PointMatchGeneric< Detection > >();

		final Pair< String, Double > ransacResult = RANSAC.computeRANSAC( candidates, inliers, gp.getModel(), rp.getMaxEpsilon(), rp.getMinInlierRatio(), rp.getMinInlierFactor(), rp.getNumIterations() );

		result.setInliers( inliers, ransacResult.getB() );

		result.setResult( System.currentTimeMillis(), ransacResult.getA() );

		return result;
	}
}
