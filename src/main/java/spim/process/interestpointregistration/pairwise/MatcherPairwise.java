package spim.process.interestpointregistration.pairwise;

import java.util.List;

import spim.fiji.spimdata.interestpoints.InterestPoint;

public interface MatcherPairwise
{
	public PairwiseResult match( final List< ? extends InterestPoint > listAIn, final List< ? extends InterestPoint > listBIn );
}
