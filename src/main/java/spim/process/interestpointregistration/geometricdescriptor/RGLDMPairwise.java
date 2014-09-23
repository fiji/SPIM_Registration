package spim.process.interestpointregistration.geometricdescriptor;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Callable;

import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.PairwiseMatch;
import spim.process.interestpointregistration.Detection;
import spim.process.interestpointregistration.RANSAC;
import spim.process.interestpointregistration.RANSACParameters;
import spim.process.interestpointregistration.TransformationModel;

public class RGLDMPairwise implements Callable< PairwiseMatch >
{	
	final PairwiseMatch pair;
	final TransformationModel model;
	final RANSACParameters rp;
	final RGLDMParameters dp;
	final String comparison;

	public RGLDMPairwise( final PairwiseMatch pair, final TransformationModel model, final String comparison, final RANSACParameters rp, final RGLDMParameters dp  )
	{
		this.pair = pair;
		this.rp = rp;
		this.dp = dp;
		this.model = model;
		this.comparison = comparison;
	}
	
	@Override
	public PairwiseMatch call() throws Exception
	{
		final RGLDMMatcher matcher = new RGLDMMatcher();
		
		final ArrayList< Detection > listA = new ArrayList< Detection >();
		final ArrayList< Detection > listB = new ArrayList< Detection >();
		
		for ( final InterestPoint i : pair.getListA() )
			listA.add( new Detection( i.getId(), i.getL() ) );

		for ( final InterestPoint i : pair.getListB() )
			listB.add( new Detection( i.getId(), i.getL() ) );

		final ArrayList< PointMatchGeneric< Detection > > candidates = matcher.extractCorrespondenceCandidates( 
				listA, 
				listB,
				dp.getNumNeighbors(),
				dp.getRedundancy(),
				dp.getRatioOfDistance(),
				dp.getDifferenceThreshold() );
		
		pair.setCandidates( candidates );
		
    	// compute ransac and remove inconsistent candidates
    	final ArrayList< PointMatchGeneric< Detection > > inliers = new ArrayList< PointMatchGeneric< Detection > >();

		String result = RANSAC.computeRANSAC( candidates, inliers, this.model.getModel(), rp.getMaxEpsilon(), rp.getMinInlierRatio(), rp.getMinInlierFactor(), rp.getNumIterations() );

		pair.setInliers( inliers );

    	IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): " + comparison + ": " + result );
		
		return pair;
	}

}
