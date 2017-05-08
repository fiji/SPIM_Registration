package spim.process.interestpointregistration.pairwise.methods.geometrichashing;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.pairwise.MatcherPairwise;
import spim.process.interestpointregistration.pairwise.PairwiseResult;
import spim.process.interestpointregistration.pairwise.methods.ransac.RANSAC;
import spim.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;

public class GeometricHashingPairwise< I extends InterestPoint > implements MatcherPairwise< I >
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
	public PairwiseResult< I > match( final List< I > listAIn, final List< I > listBIn )
	{
		final PairwiseResult< I > result = new PairwiseResult<>();
		final GeometricHasher< I > hasher = new GeometricHasher<>();
		
		final ArrayList< I > listA = new ArrayList<>();
		final ArrayList< I > listB = new ArrayList<>();

		for ( final I i : listAIn )
			listA.add( i );

		for ( final I i : listBIn )
			listB.add( i );

		if ( listA.size() < 4 || listB.size() < 4 )
		{
			result.setResult( System.currentTimeMillis(), "Not enough detections to match" );
			result.setCandidates( new ArrayList< PointMatchGeneric< I > >() );
			result.setInliers( new ArrayList< PointMatchGeneric< I > >(), Double.NaN );
			return result;
		}

		final ArrayList< PointMatchGeneric< I > > candidates = hasher.extractCorrespondenceCandidates( 
				listA,
				listB,
				gp.getDifferenceThreshold(), 
				gp.getRatioOfDistance(), 
				gp.getUseAssociatedBeads() );

		result.setCandidates( candidates );

		// compute ransac and remove inconsistent candidates
		final ArrayList< PointMatchGeneric< I > > inliers = new ArrayList<>();

		final Pair< String, Double > ransacResult = RANSAC.computeRANSAC( candidates, inliers, gp.getModel(), rp.getMaxEpsilon(), rp.getMinInlierRatio(), rp.getMinInlierFactor(), rp.getNumIterations() );

		result.setInliers( inliers, ransacResult.getB() );

		result.setResult( System.currentTimeMillis(), ransacResult.getA() );

		return result;
	}
}
