package spim.process.interestpointregistration.pairwise;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import mpicbg.icp.ICP;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.TranslationModel3D;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.util.Util;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.headless.registration.icp.IterativeClosestPointParameters;
import spim.process.interestpointregistration.Detection;

/**
 * Iterative closest point implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class IterativeClosestPointPairwise implements MatcherPairwise
{
	final IterativeClosestPointParameters ip;

	public IterativeClosestPointPairwise( final IterativeClosestPointParameters ip  )
	{
		this.ip = ip;
	}

	@Override
	public PairwiseResult match( final List< InterestPoint > listAIn, final List< InterestPoint > listBIn )
	{
		final PairwiseResult result = new PairwiseResult();

		final ArrayList< Detection > listA = new ArrayList< Detection >();
		final ArrayList< Detection > listB = new ArrayList< Detection >();

		for ( final InterestPoint i : listAIn )
			listA.add( new Detection( i.getId(), i.getL() ) );

		for ( final InterestPoint i : listBIn )
			listB.add( new Detection( i.getId(), i.getL() ) );

		// identity transform
		Model<?> model = this.ip.getModel();

		if ( listA.size() < model.getMinNumMatches() || listB.size() < model.getMinNumMatches() )
		{
			result.setResult( System.currentTimeMillis(), "Not enough detections to match" );
			result.setCandidates( new ArrayList< PointMatchGeneric< Detection > >() );
			result.setInliers( new ArrayList< PointMatchGeneric< Detection > >(), Double.NaN );
			return result;
		}

		// use the world and not the local coordinates
		for ( final Detection d : listA )
			d.setUseW( true );

		for ( final Detection d : listB )
			d.setUseW( true );

		final ICP< Detection > icp = new ICP< Detection >( listA, listB, (float)ip.getMaxDistance() );

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
				failWith( result, "ICP", "NotEnoughDataPointsException", e );
			}
			catch ( IllDefinedDataPointsException e )
			{
				failWith( result, "ICP", "IllDefinedDataPointsException", e );
			}
			catch ( NoSuitablePointsException e )
			{
				failWith( result, "ICP", "NoSuitablePointsException", e );
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

		result.setCandidates( inliers );
		result.setInliers( inliers, icp.getAverageError() );

		result.setResult( System.currentTimeMillis(), "Found " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + " after " + i + " iterations" );

		return result;
	}

	public static void failWith( final PairwiseResult result, final String algo, final String exType, final Exception e )
	{
		result.setResult( System.currentTimeMillis(), algo + " failed with " + exType + " matching " );
		
		result.setCandidates( new ArrayList< PointMatchGeneric< Detection > >() );
		result.setInliers( new ArrayList< PointMatchGeneric< Detection > >(), Double.NaN );
	}

	public static void main( final String[] args ) throws Exception
	{
		// test ICP
		final ArrayList< Detection > listA = new ArrayList< Detection >();
		final ArrayList< Detection > listB = new ArrayList< Detection >();
		
		listA.add( new Detection( 0, new double[]{ 10, 10, 0 } ) );
		listB.add( new Detection( 0, new double[]{ 11, 13, 0 } ) ); // d = 3.16

		final Random rnd = new Random( 43534 );
		final float maxError = 4;

		for ( int i = 0; i < 5; ++i )
		{
			final float x = rnd.nextFloat() * 10000 + 150;
			final float y = rnd.nextFloat() * 10000 + 150;
			
			listA.add( new Detection( i, new double[]{ x, y, 0 } ) );
			listB.add( new Detection( i, new double[]{ x + 2, y + 4, 0 } ) ); // d = 4.472, will be less than 4 once the first one matched
		}

		// use the world and not the local coordinates
		for ( int i = 0; i < listA.size(); ++ i )
		{
			listA.get( i ).setUseW( true );
			listB.get( i ).setUseW( true );
			IOFunctions.println( Util.printCoordinates( listA.get( i ).getL() ) + " >>> " + Util.printCoordinates( listB.get( i ).getL() ) + ", d=" + Point.distance( listA.get( i ), listB.get( i ) ) );
		}

		final ICP< Detection > icp = new ICP< Detection >( listA, listB, maxError );
		
		// identity transform
		TranslationModel3D model = new TranslationModel3D();

		int i = 0;
		double lastAvgError = 0;
		int lastNumCorresponding = 0;

		boolean converged = false;

		do
		{
			System.out.println( "\n" + i );
			System.out.println( "lastModel: " + model.toString() );

			try
			{
				icp.runICPIteration( model, model );
			}
			catch ( NotEnoughDataPointsException e )
			{
				throw new NotEnoughDataPointsException( e );
			}
			catch ( IllDefinedDataPointsException e )
			{
				throw new IllDefinedDataPointsException( e );
			}
			catch ( NoSuitablePointsException e )
			{
				throw new NoSuitablePointsException( e.toString() );
			}

			System.out.println( "newModel: " + model.toString() );

			System.out.println( "lastError: " + lastAvgError + ", lastNumCorresponding: " + lastNumCorresponding );
			System.out.println( "thisError: " + icp.getAverageError() + ", thisNumCorresponding: " + icp.getAverageError() );

			if ( lastNumCorresponding == icp.getNumPointMatches() && lastAvgError == icp.getAverageError() )
				converged = true;

			lastNumCorresponding = icp.getNumPointMatches();
			lastAvgError = icp.getAverageError();
			
			System.out.println( i + ": " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + ", max error [px] " + icp.getMaximalError() );
		}
		while ( !converged && ++i < 100 );
		

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Found " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + " after " + i + " iterations" );
	}

}
