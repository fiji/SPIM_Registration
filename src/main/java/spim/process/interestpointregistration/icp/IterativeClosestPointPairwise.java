package spim.process.interestpointregistration.icp;

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.Callable;

import net.imglib2.util.Util;
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
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.Detection;
import spim.process.interestpointregistration.PairwiseMatch;
import spim.process.interestpointregistration.TransformationModel;

public class IterativeClosestPointPairwise implements Callable< PairwiseMatch >
{
	final PairwiseMatch pair;
	final TransformationModel model;
	final IterativeClosestPointParameters ip;
	final String comparison;

	public IterativeClosestPointPairwise( final PairwiseMatch pair, final TransformationModel model, final String comparison, final IterativeClosestPointParameters ip  )
	{
		this.pair = pair;
		this.ip = ip;
		this.model = model;
		this.comparison = comparison;
	}

	@Override
	public PairwiseMatch call()
	{
		final ArrayList< Detection > listA = new ArrayList< Detection >();
		final ArrayList< Detection > listB = new ArrayList< Detection >();
		
		for ( final InterestPoint i : pair.getListA() )
			listA.add( new Detection( i.getId(), i.getL() ) );

		for ( final InterestPoint i : pair.getListB() )
			listB.add( new Detection( i.getId(), i.getL() ) );

		// identity transform
		Model<?> model = this.model.getModel();

		if ( listA.size() < model.getMinNumMatches() || listB.size() < model.getMinNumMatches() )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): " + comparison + ": "
					+ "Not enough detections to match (" + ( model.getMinNumMatches() ) +
					" required per list, |listA|= " + listA.size() + ", |listB|= " + listB.size() + ")" );
			pair.setCandidates( new ArrayList< PointMatchGeneric< Detection > >() );
			pair.setInliers( new ArrayList< PointMatchGeneric< Detection > >(), Double.NaN );
			return pair;
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
				failWith( "ICP", "NotEnoughDataPointsException", pair, e );
				return pair;
			}
			catch ( IllDefinedDataPointsException e )
			{
				failWith( "ICP", "IllDefinedDataPointsException", pair, e );
				return pair;
			}
			catch ( NoSuitablePointsException e )
			{
				failWith( "ICP", "NoSuitablePointsException", pair, e );
				return pair;
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
		pair.setInliers( inliers, icp.getAverageError() );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): " + comparison + ": Found " + icp.getNumPointMatches() + " matches, avg error [px] " + icp.getAverageError() + " after " + i + " iterations" );

		return pair;
	}

	public static void failWith( final String algo, final String exType, final PairwiseMatch pair, final Exception e )
	{
		IOFunctions.println(
				algo + " failed with " + exType + " matching " + 
				"TP=" + pair.getViewIdA().getTimePointId() + ", ViewSetup=" + pair.getViewIdA().getViewSetupId() + " to " + 
				"TP=" + pair.getViewIdB().getTimePointId() + ", ViewSetup=" + pair.getViewIdB().getViewSetupId() + ": " + e );
		
		pair.setCandidates( new ArrayList< PointMatchGeneric< Detection > >() );
		pair.setInliers( new ArrayList< PointMatchGeneric< Detection > >(), Double.NaN );
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
