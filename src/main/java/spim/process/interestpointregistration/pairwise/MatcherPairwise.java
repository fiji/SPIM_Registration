package spim.process.interestpointregistration.pairwise;

import java.util.List;

import spim.fiji.spimdata.interestpoints.InterestPoint;

public interface MatcherPairwise< I extends InterestPoint >
{
	public PairwiseResult< I > match( final List< I > listAIn, final List< I > listBIn );
}
