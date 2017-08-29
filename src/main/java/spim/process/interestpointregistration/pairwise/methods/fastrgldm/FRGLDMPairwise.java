package spim.process.interestpointregistration.pairwise.methods.fastrgldm;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.pairwise.MatcherPairwise;
import spim.process.interestpointregistration.pairwise.PairwiseResult;
import spim.process.interestpointregistration.pairwise.methods.ransac.RANSAC;
import spim.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;

public class FRGLDMPairwise< I extends InterestPoint > implements MatcherPairwise< I >
{
	final RANSACParameters rp;
	final FRGLDMParameters fp;

	public FRGLDMPairwise(
			final RANSACParameters rp,
			final FRGLDMParameters fp )
	{ 
		this.rp = rp;
		this.fp = fp;
	}

	@Override
	public PairwiseResult< I > match( final List< I > listAIn, final List< I > listBIn )
	{
		final PairwiseResult< I > result = new PairwiseResult<>( true );
		final FRGLDMMatcher< I > hasher = new FRGLDMMatcher<>();
		
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
				fp.getRedundancy(),
				fp.getRatioOfDistance() );

		result.setCandidates( candidates );

		// compute ransac and remove inconsistent candidates
		final ArrayList< PointMatchGeneric< I > > inliers = new ArrayList<>();

		final Pair< String, Double > ransacResult = RANSAC.computeRANSAC( candidates, inliers, fp.getModel(), rp.getMaxEpsilon(), rp.getMinInlierRatio(), rp.getMinInlierFactor(), rp.getNumIterations() );

		result.setInliers( inliers, ransacResult.getB() );

		result.setResult( System.currentTimeMillis(), ransacResult.getA() );

		return result;
	}

	/**
	 * We run RANSAC on these points which makes copies, so no need to duplicate points
	 */
	@Override
	public boolean requiresInterestPointDuplication() { return false; }
}
