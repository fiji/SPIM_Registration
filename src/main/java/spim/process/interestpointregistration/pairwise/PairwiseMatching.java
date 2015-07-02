package spim.process.interestpointregistration.pairwise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mpicbg.spim.data.sequence.ViewId;
import spim.Threads;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.headless.registration.PairwiseResult;

public class PairwiseMatching
{
	public static List< Pair< Pair< ViewId, ViewId >, PairwiseResult > > computePairs(
			final List< Pair< ViewId, ViewId > > pairs,
			final Map< ViewId, InterestPointList > interestpoints,
			final PairwiseInterestPointMatcher matcher )
	{
		return computePairs( pairs, interestpoints, matcher, null );
	}

	public static List< Pair< Pair< ViewId, ViewId >, PairwiseResult > > computePairs(
			final List< Pair< ViewId, ViewId > > pairs,
			final Map< ViewId, InterestPointList > interestpoints,
			final PairwiseInterestPointMatcher matcher,
			final ExecutorService exec )
	{
		final ExecutorService taskExecutor;
		
		if ( exec == null )
			taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		else
			taskExecutor = exec;

		final ArrayList< Callable< PairwiseResult > > tasks = new ArrayList< Callable< PairwiseResult > >(); // your tasks

		for ( final Pair< ViewId, ViewId > pair : pairs )
		{
			/*
			// just for logging the names and results of pairwise comparison
			final ViewDescription viewA = spimData.getSequenceDescription().getViewDescription( pair.getViewIdA() );
			final ViewDescription viewB = spimData.getSequenceDescription().getViewDescription( pair.getViewIdB() );

			final String description = "[TP=" + viewA.getTimePoint().getName() + 
					" angle=" + viewA.getViewSetup().getAngle().getName() + ", ch=" + viewA.getViewSetup().getChannel().getName() +
					", illum=" + viewA.getViewSetup().getIllumination().getName() + " >>> TP=" + viewB.getTimePoint().getName() +
					" angle=" + viewB.getViewSetup().getAngle().getName() + ", ch=" + viewB.getViewSetup().getChannel().getName() +
					", illum=" + viewB.getViewSetup().getIllumination().getName() + "]";
			*/

			final InterestPointList listA = interestpoints.get( pair.getA() );
			final InterestPointList listB = interestpoints.get( pair.getB() );

			tasks.add( new Callable< PairwiseResult >()
			{
				@Override
				public PairwiseResult call() throws Exception
				{
					return matcher.match( listA, listB );
				}
			});
		}

		final List< Pair< Pair< ViewId, ViewId >, PairwiseResult > > r =
				new ArrayList< Pair< Pair< ViewId, ViewId >, PairwiseResult > >();
		try
		{
			// invokeAll() returns when all tasks are complete
			List< Future< PairwiseResult > > futures = taskExecutor.invokeAll( tasks );

			for ( int i = 0; i < pairs.size(); ++i )
			{
				r.add( new ValuePair< Pair< ViewId, ViewId >, PairwiseResult >( pairs.get( i ), futures.get( i ).get() ) );
			}
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}

		if ( exec == null )
			taskExecutor.shutdown();

		// TODO:
		return r;
	}
}
