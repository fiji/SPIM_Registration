package spim.process.interestpointregistration;

import java.text.NumberFormat;
import java.util.ArrayList;

import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import mpicbg.pointdescriptor.LinkedPoint;
import mpicbg.spim.mpicbg.PointMatchGeneric;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class RANSAC
{
	public static String computeRANSAC( 
			final ArrayList< PointMatchGeneric < Detection > > correspondenceCandidates, 
			final ArrayList< PointMatchGeneric < Detection > > inlierList, 
			final Model<?> model, 
			final float maxEpsilon, 
			final float minInlierRatio, 
			final float minNumberInlierFactor, 
			final int numIterations )
	{
		final int numCorrespondences = correspondenceCandidates.size();
		final int minNumCorrespondences = Math.max( model.getMinNumMatches(), Math.round( model.getMinNumMatches() * minNumberInlierFactor ) );
		
		/*
		 * First remove the inconsistent correspondences
		 */
		// I do not think anymore that this is required
		// removeInconsistentCorrespondences( correspondenceCandidates );

		// if there are not enough correspondences for the used model
		if ( numCorrespondences < minNumCorrespondences )
			return "Not enough correspondences found " + numCorrespondences + ", should be at least " + minNumCorrespondences;

		/**
		 * The ArrayList that stores the inliers after RANSAC, contains PointMatches of LinkedPoints
		 * so that MultiThreading is possible
		 */
		//final ArrayList< PointMatchGeneric<LinkedPoint<T>> > candidates = new ArrayList<PointMatchGeneric<LinkedPoint<T>>>();		
		final ArrayList< PointMatch > candidates = new ArrayList< PointMatch >();
		final ArrayList< PointMatch > inliers = new ArrayList< PointMatch >();
		
		// clone the beads for the RANSAC as we are working multithreaded and they will be modified
		for ( final PointMatchGeneric< Detection > correspondence : correspondenceCandidates )
		{
			final Detection detectionA = correspondence.getPoint1();
			final Detection detectionB = correspondence.getPoint2();
			
			final LinkedPoint< Detection > pA = new LinkedPoint< Detection >( detectionA.getL(), detectionA.getW(), detectionA );
			final LinkedPoint< Detection > pB = new LinkedPoint< Detection >( detectionB.getL(), detectionB.getW(), detectionB );
			final float weight = correspondence.getWeight(); 

			candidates.add( new PointMatchGeneric< LinkedPoint< Detection > >( pA, pB, weight ) );
		}
		
		boolean modelFound = false;
		
		try
		{
			/*modelFound = m.ransac(
  					candidates,
					inliers,
					numIterations,
					maxEpsilon, minInlierRatio );*/
		
			modelFound = model.filterRansac(
					candidates,
					inliers,
					numIterations,
					maxEpsilon, minInlierRatio ); 
		}
		catch ( NotEnoughDataPointsException e )
		{
			return e.toString();
		}
			
		final NumberFormat nf = NumberFormat.getPercentInstance();
		final float ratio = ((float)inliers.size() / (float)candidates.size());
		
		if ( modelFound && inliers.size() >= minNumCorrespondences )
		{			
			for ( final PointMatch pointMatch : inliers )
			{
				@SuppressWarnings("unchecked")
				final PointMatchGeneric<LinkedPoint< Detection > > pm = (PointMatchGeneric< LinkedPoint< Detection > >) pointMatch;
				
				final Detection detectionA = pm.getPoint1().getLinkedObject();
				final Detection detectionB = pm.getPoint2().getLinkedObject();
				
				inlierList.add( new PointMatchGeneric< Detection >( detectionA, detectionB ) );
			}

			return "Remaining inliers after RANSAC: " + inliers.size() + " of " + candidates.size() + " (" + nf.format(ratio) + ") with average error " + model.getCost();
		}
		else
		{
			if ( modelFound )					
				return "Model found but not enough remaining inliers (" + inliers.size() + "/" + minNumCorrespondences + ") after RANSAC of " + candidates.size();
			else
				return "NO Model found after RANSAC of " + candidates.size();
		}
	}
}
