package spim.process.interestpointregistration.geometricdescriptor;

import fiji.util.KDTree;
import fiji.util.NNearestNeighborSearch;

import java.util.ArrayList;

import mpicbg.pointdescriptor.AbstractPointDescriptor;
import mpicbg.pointdescriptor.SimplePointDescriptor;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.pointdescriptor.matcher.Matcher;
import mpicbg.pointdescriptor.matcher.SubsetMatcher;
import mpicbg.pointdescriptor.similarity.SimilarityMeasure;
import mpicbg.pointdescriptor.similarity.SquareDistance;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.process.interestpointregistration.Detection;

public class RGLDMMatcher
{
	public ArrayList< PointMatchGeneric< Detection > > extractCorrespondenceCandidates( 
			final ArrayList< Detection > nodeListA, 
			final ArrayList< Detection > nodeListB, 
			final int numNeighbors,
			final int redundancy,
			final double ratioOfDistance,
			final double differenceThreshold ) 
	{
		/* create KDTrees */	
		final KDTree< Detection > treeA = new KDTree< Detection >( nodeListA );
		final KDTree< Detection > treeB = new KDTree< Detection >( nodeListB );
		
		/* extract point descriptors */
		final Matcher matcher = new SubsetMatcher( numNeighbors, numNeighbors + redundancy );
		final int numRequiredNeighbors = matcher.getRequiredNumNeighbors();
		
		final SimilarityMeasure similarityMeasure = new SquareDistance();
		
		final ArrayList< SimplePointDescriptor<Detection> > descriptorsA = createSimplePointDescriptors( treeA, nodeListA, numRequiredNeighbors, matcher, similarityMeasure );
		final ArrayList< SimplePointDescriptor<Detection> > descriptorsB = createSimplePointDescriptors( treeB, nodeListB, numRequiredNeighbors, matcher, similarityMeasure );

		return findCorrespondingDescriptors( descriptorsA, descriptorsB, ratioOfDistance, differenceThreshold );
	}
	
	protected static final <D extends AbstractPointDescriptor<Detection, D>> ArrayList<PointMatchGeneric< Detection >> findCorrespondingDescriptors(
			final ArrayList<D> descriptorsA,
			final ArrayList<D> descriptorsB,
			final double nTimesBetter,
			final double differenceThreshold )
	{
		final ArrayList<PointMatchGeneric< Detection >> correspondenceCandidates = new ArrayList<PointMatchGeneric< Detection >>();
		
		for ( final D descriptorA : descriptorsA )
		{
			double bestDifference = Double.MAX_VALUE;			
			double secondBestDifference = Double.MAX_VALUE;
			
			D bestMatch = null;
			D secondBestMatch = null;

			for ( final D descriptorB : descriptorsB )
			{
				final double difference = descriptorA.descriptorDistance( descriptorB );

				if ( difference < secondBestDifference )
				{					
					secondBestDifference = difference;
					secondBestMatch = descriptorB;
					
					if ( secondBestDifference < bestDifference )
					{
						double tmpDiff = secondBestDifference;
						D tmpMatch = secondBestMatch;
						
						secondBestDifference = bestDifference;
						secondBestMatch = bestMatch;
						
						bestDifference = tmpDiff;
						bestMatch = tmpMatch;
					}
				}				
			}
			
			if ( bestDifference < differenceThreshold && bestDifference * nTimesBetter < secondBestDifference )
			{	
				// add correspondence for the two basis points of the descriptor
				Detection detectionA = descriptorA.getBasisPoint();
				Detection detectionB = bestMatch.getBasisPoint();
				
				// for RANSAC
				correspondenceCandidates.add( new PointMatchGeneric<Detection>( detectionA, detectionB ) );				
			}
		}
		
		return correspondenceCandidates;
	}

	protected static ArrayList< SimplePointDescriptor<Detection> > createSimplePointDescriptors( final KDTree< Detection > tree, final ArrayList< Detection > basisPoints, 
			final int numNeighbors, final Matcher matcher, final SimilarityMeasure similarityMeasure )
	{
		final NNearestNeighborSearch< Detection > nnsearch = new NNearestNeighborSearch< Detection >( tree );
		final ArrayList< SimplePointDescriptor<Detection> > descriptors = new ArrayList< SimplePointDescriptor<Detection> > ( );
		
		for ( final Detection p : basisPoints )
		{
			final ArrayList< Detection > neighbors = new ArrayList< Detection >();
			final Detection neighborList[] = nnsearch.findNNearestNeighbors( p, numNeighbors + 1 );
			
			// the first hit is always the point itself
			for ( int n = 1; n < neighborList.length; ++n )
				neighbors.add( neighborList[ n ] );
			
			try
			{
				descriptors.add( new SimplePointDescriptor<Detection>( p, neighbors, similarityMeasure, matcher ) );
			}
			catch ( NoSuitablePointsException e )
			{
				e.printStackTrace();
			}
		}
		
		return descriptors;
	}

}
