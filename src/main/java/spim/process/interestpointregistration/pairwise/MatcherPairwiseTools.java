package spim.process.interestpointregistration.pairwise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.Threads;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class MatcherPairwiseTools
{
	public static < V, I extends InterestPoint > List< Pair< Pair< V, V >, PairwiseResult< I > > > computePairs(
			final List< Pair< V, V > > pairs,
			final Map< V, List< I > > interestpoints,
			final MatcherPairwise< I > matcher )
	{
		return computePairs( pairs, interestpoints, matcher, null );
	}

	public static void assignLoggingDescriptions(
			final Pair< ?, ? > p, PairwiseResult< ? > pwr )
	{
		if ( ViewId.class.isInstance( p.getA() ) && ViewId.class.isInstance( p.getB() ) )
		{
			final String description =
					"[TP=" + ((ViewId)p.getA()).getTimePointId() +
					" ViewId=" + ((ViewId)p.getA()).getViewSetupId() +
					" >>> TP=" + ((ViewId)p.getB()).getTimePointId() +
					" ViewId=" + ((ViewId)p.getB()).getViewSetupId() + "]";

			pwr.setDescription( description );
		}
		else if ( Group.class.isInstance( p.getA() ) && Group.class.isInstance( p.getB() ) )
		{
			pwr.setDescription( "[Group {" + p.getA() + "} >>> Group {" + p.getB() + "}]" );
		}
		else
		{
			pwr.setDescription( "[" + p.getA() + " >>> " + p.getB() + "]" );
		}
	}

	public static < V, I extends InterestPoint > List< Pair< Pair< V, V >, PairwiseResult< I > > > computePairs(
			final List< Pair< V, V > > pairs,
			final Map< V, ? extends List< I > > interestpoints,
			final MatcherPairwise< I > matcher,
			final ExecutorService exec )
	{
		final ExecutorService taskExecutor;
		
		if ( exec == null )
			taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		else
			taskExecutor = exec;

		final ArrayList< Callable< PairwiseResult< I > > > tasks = new ArrayList<>(); // your tasks

		for ( final Pair< V, V > pair : pairs )
		{
			final List< I > listA = interestpoints.get( pair.getA() );
			final List< I > listB = interestpoints.get( pair.getB() );

			tasks.add( new Callable< PairwiseResult< I > >()
			{
				@Override
				public PairwiseResult< I > call() throws Exception
				{
					final PairwiseResult< I > pwr = matcher.match( listA, listB );
					assignLoggingDescriptions( pair, pwr );
					return pwr;
				}
			});
		}

		final List< Pair< Pair< V, V >, PairwiseResult< I > > > r = new ArrayList<>();

		try
		{
			// invokeAll() returns when all tasks are complete
			List< Future< PairwiseResult< I > > > futures = taskExecutor.invokeAll( tasks );

			for ( int i = 0; i < pairs.size(); ++i )
			{
				final PairwiseResult< I > pwr = futures.get( i ).get();
				final Pair< V, V > pair = pairs.get( i );
				r.add( new ValuePair< Pair< V, V >, PairwiseResult< I > >( pair, pwr ) );
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
