package spim.process.interestpointregistration.icp;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Callable;

import mpicbg.icp.ICP;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.Detection;
import spim.process.interestpointregistration.TransformationModel;

public class IterativeClosestPointPairwise implements Callable< ChannelInterestPointListPair >
{
	final ChannelInterestPointListPair pair;
	final TransformationModel model;
	final IterativeClosestPointParameters ip;
	final String comparison;

	public IterativeClosestPointPairwise( final ChannelInterestPointListPair pair, final TransformationModel model, final String comparison, final IterativeClosestPointParameters ip  )
	{
		this.pair = pair;
		this.ip = ip;
		this.model = model;
		this.comparison = comparison;
	}

	@Override
	public ChannelInterestPointListPair call() throws Exception
	{
		final ArrayList< Detection > listA = new ArrayList< Detection >();
		final ArrayList< Detection > listB = new ArrayList< Detection >();
		
		for ( final InterestPoint i : pair.getListA() )
			listA.add( new Detection( i.getId(), i.getL() ) );

		for ( final InterestPoint i : pair.getListB() )
			listB.add( new Detection( i.getId(), i.getL() ) );

		final ICP< Detection > icp = new ICP<Detection>( listA, listB, (float)ip.getMaxDistance() );
		
		// identity transform
		Model<?> model = this.model.getModel();

		int i = 0;
		double lastAvgError = 0;
		int lastNumCorresponding = 0;

		boolean converged = false;

		do
		{
			try
			{
				icp.runICPIteration( model, model );
			}
			catch ( NotEnoughDataPointsException e )
			{
				failWith( "ICP", "NotEnoughDataPointsException", pair, e );
				throw new NotEnoughDataPointsException( e );
			}
			catch ( IllDefinedDataPointsException e )
			{
				failWith( "ICP", "IllDefinedDataPointsException", pair, e );
				throw new IllDefinedDataPointsException( e );
			}
			catch ( NoSuitablePointsException e )
			{
				failWith( "ICP", "NoSuitablePointsException", pair, e );
				throw new NoSuitablePointsException( e.toString() );
			}

			if ( lastNumCorresponding == icp.getNumPointMatches() && lastAvgError == icp.getAverageError() )
				converged = true;

			lastNumCorresponding = icp.getNumPointMatches();
			lastAvgError = icp.getAverageError();
			
			System.out.println( i + ": " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + ", max error [px] " + icp.getMaximalError() );
		}
		while ( !converged && ++i < ip.getMaxNumIterations() );
		
    	final ArrayList< PointMatchGeneric< Detection > > inliers = new ArrayList< PointMatchGeneric< Detection > >();
    	
    	for ( final PointMatch pm : icp.getPointMatches() )
    		inliers.add( new PointMatchGeneric<Detection>( (Detection)pm.getP1(), (Detection)pm.getP2() ) );
    	
		pair.setCandidates( inliers );
		pair.setInliers( inliers );

    	IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): " + comparison + ": Found " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + " after " + i + " iterations" );
    	
		return pair;
	}

	public static void failWith( final String algo, final String exType, final ChannelInterestPointListPair pair, final Exception e )
	{
		IOFunctions.println(
				algo + " failed with " + exType + " matching " + 
				"TP=" + pair.getViewIdA().getTimePointId() + ", ViewSetup=" + pair.getViewIdA().getViewSetupId() + " to " + 
				"TP=" + pair.getViewIdB().getTimePointId() + ", ViewSetup=" + pair.getViewIdB().getViewSetupId() + ": " + e );
		
		pair.setCandidates( new ArrayList<PointMatchGeneric<Detection>>() );
		pair.setInliers( new ArrayList<PointMatchGeneric<Detection>>() );
	}
}
