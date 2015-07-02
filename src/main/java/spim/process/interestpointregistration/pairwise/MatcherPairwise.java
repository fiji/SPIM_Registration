package spim.process.interestpointregistration.pairwise;

import java.util.List;

import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.headless.registration.PairwiseResult;

public interface MatcherPairwise
{
	public PairwiseResult match( final List< InterestPoint > listAIn, final List< InterestPoint > listBIn );
}
