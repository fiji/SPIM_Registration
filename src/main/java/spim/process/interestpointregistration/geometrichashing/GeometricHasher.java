package spim.process.interestpointregistration.geometrichashing;

import fiji.util.KDTree;
import fiji.util.NNearestNeighborSearch;
import fiji.util.node.Leaf;

import java.util.ArrayList;

import mpicbg.models.Point;
import mpicbg.pointdescriptor.LocalCoordinateSystemPointDescriptor;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.process.interestpointregistration.Detection;

/**
 * Class that actually computes the geometric hashing
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class GeometricHasher
{
	public ArrayList< PointMatchGeneric< Detection > > extractCorrespondenceCandidates( 
			final ArrayList< Detection > nodeListA, 
			final ArrayList< Detection > nodeListB, 
			double differenceThreshold, 
			double ratioOfDistance, 
			final boolean useAssociatedBeads ) 
	{
		final int numNeighbors = 3;
		
		final KDTree< Detection > tree1 = new KDTree< Detection >( nodeListA );
		final KDTree< Detection > tree2 = new KDTree< Detection >( nodeListB );

		final ArrayList< LocalCoordinateSystemPointDescriptor< Detection > > descriptors1 = 
			createLocalCoordinateSystemPointDescriptors( tree1, nodeListA, numNeighbors, false );
		
		final ArrayList< LocalCoordinateSystemPointDescriptor< Detection > > descriptors2 = 
			createLocalCoordinateSystemPointDescriptors( tree2, nodeListB, numNeighbors, false );
		
		// create lookup tree for descriptors2		
		final KDTree< LocalCoordinateSystemPointDescriptor< Detection > > lookUpTree2 = new KDTree< LocalCoordinateSystemPointDescriptor< Detection > >( descriptors2 );
		final NNearestNeighborSearch< LocalCoordinateSystemPointDescriptor< Detection > > nnsearch = new NNearestNeighborSearch< LocalCoordinateSystemPointDescriptor< Detection > >( lookUpTree2 );
	
		// store the candidates for corresponding beads
		final ArrayList<PointMatchGeneric< Detection >> correspondences = new ArrayList<PointMatchGeneric<Detection>>();
		
		/* compute matching */
		computeMatching( descriptors1, nnsearch, correspondences, differenceThreshold, ratioOfDistance );
		
		return correspondences;
	}
	
	protected void computeMatching( 
			final ArrayList< LocalCoordinateSystemPointDescriptor< Detection > > descriptors1, 
			final NNearestNeighborSearch< LocalCoordinateSystemPointDescriptor< Detection > > nnsearch2,
			final ArrayList<PointMatchGeneric<Detection>> correspondences, 
			final double differenceThreshold, 
			final double ratioOfDistance )
	{
		//System.out.println( "BeadA" + "\t" + "BeadB1" + "\t" + "BeadB2" + "\t" + "Diff1" + "\t" + "Diff2" );

		for ( final LocalCoordinateSystemPointDescriptor< Detection > descriptorA : descriptors1 )
		{
			final LocalCoordinateSystemPointDescriptor< Detection > matches[] = nnsearch2.findNNearestNeighbors( descriptorA, 2 );

			double best = descriptorA.descriptorDistance( matches[ 0 ] );
			double secondBest = descriptorA.descriptorDistance( matches[ 1 ] );

			if ( best < differenceThreshold && best * ratioOfDistance <= secondBest )
			{
				final Detection detectionA = descriptorA.getBasisPoint();
				final Detection detectionB = matches[ 0 ].getBasisPoint();

				//System.out.println( beadA.getID() + "\t" + matches[ 0 ].getBasisPoint().getID() + "\t" + matches[ 1 ].getBasisPoint().getID() + "\t" + best + "\t" + secondBest );

				//detectionA.addPointDescriptorCorrespondence( detectionB, 1 );
				//detectionB.addPointDescriptorCorrespondence( detectionA, 1 );
				
				correspondences.add( new PointMatchGeneric<Detection>( detectionA, detectionB, 1 ) );
			}				
		}					
		
		//System.exit( 0 );
	}

	public static <P extends Point & Leaf<P>> ArrayList< LocalCoordinateSystemPointDescriptor< P > > createLocalCoordinateSystemPointDescriptors( 
			final KDTree< P > tree, 
            final ArrayList< P > basisPoints, 
            final int numNeighbors,
            final boolean normalize )
	{
		final NNearestNeighborSearch< P > nnsearch = new NNearestNeighborSearch< P >( tree );
		final ArrayList< LocalCoordinateSystemPointDescriptor< P > > descriptors = new ArrayList< LocalCoordinateSystemPointDescriptor< P > > ( );
		
		for ( final P point : basisPoints )
		{
			final ArrayList< P > neighbors = new ArrayList< P >();
			final P neighborList[] = nnsearch.findNNearestNeighbors( point, numNeighbors + 1 );
			
			// the first hit is always the point itself
			for ( int n = 1; n < neighborList.length; ++n )
			neighbors.add( neighborList[ n ] );
			
			try
			{
				descriptors.add( new LocalCoordinateSystemPointDescriptor<P>( point, neighbors, normalize ) );
			}
			catch ( NoSuitablePointsException e )
			{
				e.printStackTrace();
			}
		}
		
		return descriptors;
	}
}
