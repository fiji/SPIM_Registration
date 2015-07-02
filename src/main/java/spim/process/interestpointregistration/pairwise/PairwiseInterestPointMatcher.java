package spim.process.interestpointregistration.pairwise;

import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.headless.registration.PairwiseResult;

public interface PairwiseInterestPointMatcher
{
	public PairwiseResult match( final InterestPointList listA, final InterestPointList listB );
}
