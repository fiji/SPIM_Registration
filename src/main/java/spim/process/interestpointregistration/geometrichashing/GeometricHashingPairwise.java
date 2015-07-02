package spim.process.interestpointregistration.geometrichashing;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Callable;

import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.headless.registration.PairwiseResult;
import spim.headless.registration.RANSACParameters;
import spim.headless.registration.geometrichashing.GeometricHashingParameters;
import spim.process.interestpointregistration.Detection;
import spim.process.interestpointregistration.RANSAC;

public class GeometricHashingPairwise implements Callable< PairwiseResult >
{
	final InterestPointList listA;
	final InterestPointList listB;
	final PairwiseResult result;
	final RANSACParameters rp;
	final GeometricHashingParameters gp;
	
	public GeometricHashingPairwise(
			final InterestPointList listA,
			final InterestPointList listB,
			final RANSACParameters rp,
			final GeometricHashingParameters gp )
	{ 
		this.listA = listA;
		this.listB = listB;
		this.result = new PairwiseResult();
		this.rp = rp;
		this.gp = gp;
	}

	@Override
	public PairwiseResult call() throws Exception 
	{
		final GeometricHasher hasher = new GeometricHasher();
		
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
			result.result = "(" + new Date( System.currentTimeMillis() ) + "): " /* + comparison */ + ": Not enough detections to match";
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

		result.result = "(" + new Date( System.currentTimeMillis() ) + "): " + /*comparison +*/ ": " + ransacResult.getA();

		return result;
	}
}
