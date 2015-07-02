package spim.process.interestpointregistration.geometricdescriptor;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Callable;

import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.headless.registration.PairwiseResult;
import spim.headless.registration.RANSACParameters;
import spim.headless.registration.geometricdescriptor.RGLDMParameters;
import spim.process.interestpointregistration.Detection;
import spim.process.interestpointregistration.RANSAC;

public class RGLDMPairwise implements Callable< PairwiseResult >
{
	final InterestPointList listA;
	final InterestPointList listB;
	final PairwiseResult result;
	final RANSACParameters rp;
	final RGLDMParameters dp;

	public RGLDMPairwise(
			final InterestPointList listA,
			final InterestPointList listB,
			final RANSACParameters rp,
			final RGLDMParameters dp  )
	{
		this.listA = listA;
		this.listB = listB;
		this.result = new PairwiseResult();
		this.rp = rp;
		this.dp = dp;
	}
	
	@Override
	public PairwiseResult call() throws Exception
	{
		final ArrayList< Detection > listA = new ArrayList< Detection >();
		final ArrayList< Detection > listB = new ArrayList< Detection >();

		if ( this.listA.getInterestPoints() == null )
			this.listA.loadInterestPoints();

		if ( this.listB.getInterestPoints() == null )
			this.listB.loadInterestPoints();

		for ( final InterestPoint i : this.listA.getInterestPoints() )
			listA.add( new Detection( i.getId(), i.getL() ) );

		for ( final InterestPoint i : this.listB.getInterestPoints() )
			listB.add( new Detection( i.getId(), i.getL() ) );

		if ( listA.size() < 4 || listB.size() < 4 )
		{
			result.result = "(" + new Date( System.currentTimeMillis() ) + "): " /*+ comparison */ + ": Not enough detections to match";
			result.setCandidates( new ArrayList< PointMatchGeneric< Detection > >() );
			result.setInliers( new ArrayList< PointMatchGeneric< Detection > >(), Double.NaN );
			return result;
		}

		final RGLDMMatcher matcher = new RGLDMMatcher();
		final ArrayList< PointMatchGeneric< Detection > > candidates = matcher.extractCorrespondenceCandidates( 
				listA, 
				listB,
				dp.getNumNeighbors(),
				dp.getRedundancy(),
				dp.getRatioOfDistance(),
				dp.getDifferenceThreshold() );

		result.setCandidates( candidates );

		// compute ransac and remove inconsistent candidates
		final ArrayList< PointMatchGeneric< Detection > > inliers = new ArrayList< PointMatchGeneric< Detection > >();
	
		final Pair< String, Double > ransacResult = RANSAC.computeRANSAC( candidates, inliers, dp.getModel(), rp.getMaxEpsilon(), rp.getMinInlierRatio(), rp.getMinInlierFactor(), rp.getNumIterations() );
	
		result.setInliers( inliers, ransacResult.getB() );
	
		result.result = "(" + new Date( System.currentTimeMillis() ) + "): " + /*comparison +*/ ": " + ransacResult.getA();
		
		return result;
	}
}
