package spim.process.interestpointregistration.pairwise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.Threads;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;

public class MatcherPairwiseTools
{
	public static < V > HashSet< V > allViews( final Group< ? extends V > a, final Group< ? extends V > b )
	{
		final HashSet< V > all = new HashSet<>();
		all.addAll( a.getViews() );
		all.addAll( b.getViews() );

		return all;
	}

	public static < V, I extends InterestPoint > List< Pair< Pair< V, V >, PairwiseResult< I > > > computePairs(
			final List< Pair< V, V > > pairs,
			final Map< V, List< I > > interestpoints,
			final MatcherPairwise< I > matcher )
	{
		return computePairs( pairs, interestpoints, matcher, null );
	}

	public static < V extends ViewId > Map< V, List< CorrespondingInterestPoints > > clearCorrespondences( final Map< V, ? extends InterestPointList > map )
	{
		final Map< V, List< CorrespondingInterestPoints > > cMap = new HashMap<>();

		for ( final V viewId : map.keySet() )
		{
			final InterestPointList list = map.get( viewId );
			final ArrayList< CorrespondingInterestPoints > cList = new ArrayList<>();
			list.setCorrespondingInterestPoints( cList );
			cMap.put( viewId, cList );
		}

		return cMap;
	}

	public static < V extends ViewId, P extends PairwiseResult< GroupedInterestPoint< V > > >
		List< Pair< Pair< V, V >, PairwiseResult< GroupedInterestPoint< V > > > >
			addCorrespondencesFromGroups(
			final Collection< ? extends Pair< ?, P > > resultGroup,
			final Map< V, ? extends InterestPointList > iplMap,
			final Map< V, String > labelMap,
			final Map< V, ? extends List< CorrespondingInterestPoints > > cMap
			)
	{
		final HashMap< Pair< V, V >, PairwiseResult< GroupedInterestPoint< V > > > transformedMap = new HashMap<>();

		for ( final Pair< ?, P > p : resultGroup )
		{
			for ( final PointMatchGeneric< GroupedInterestPoint< V > > pm : p.getB().getInliers() )
			{
				// assign correspondences
				final GroupedInterestPoint< V > gpA = pm.getPoint1();
				final GroupedInterestPoint< V > gpB = pm.getPoint2();

				final V viewIdA = gpA.getV();
				final V viewIdB = gpB.getV();

				final String labelA = labelMap.get( viewIdA );
				final String labelB = labelMap.get( viewIdB );

				final CorrespondingInterestPoints correspondingToA = new CorrespondingInterestPoints( gpA.getId(), viewIdB, labelB, gpB.getId() );
				final CorrespondingInterestPoints correspondingToB = new CorrespondingInterestPoints( gpB.getId(), viewIdA, labelA, gpA.getId() );
				
				cMap.get( viewIdA ).add( correspondingToA );
				cMap.get( viewIdB ).add( correspondingToB );

				// update transformedMap
				final Pair< V, V > pair = new ValuePair<>( viewIdA, viewIdB );

				final PairwiseResult< GroupedInterestPoint< V > > pwr;

				if ( transformedMap.containsKey( pair ) )
					pwr = transformedMap.get( pair );
				else
				{
					pwr = new PairwiseResult<>();
					pwr.setInliers( new ArrayList<>(), p.getB().getError() );
					pwr.setCandidates( new ArrayList<>() );
					transformedMap.put( pair, pwr );
				}

				pwr.getInliers().add( pm );
			}

			System.out.println( p.getB().getFullDesc() );
		}

		for ( final V viewId : cMap.keySet() )
			iplMap.get( viewId ).setCorrespondingInterestPoints( cMap.get( viewId ) );

		final ArrayList< Pair< Pair< V, V >, PairwiseResult< GroupedInterestPoint< V > > > > transformedList = new ArrayList<>();

		for ( final Pair< V, V > pair : transformedMap.keySet() )
			transformedList.add( new ValuePair<>( pair, transformedMap.get( pair ) ) );

		return transformedList;
	}

	/**
	 * Add correspondences to the interestpointlists
	 */
	public static < I extends InterestPoint > void addCorrespondences(
			final List< PointMatchGeneric< I > > correspondences,
			final ViewId viewIdA,
			final ViewId viewIdB,
			final String labelA,
			final String labelB,
			final InterestPointList listA,
			final InterestPointList listB )
	{
		final List< CorrespondingInterestPoints > corrListA, corrListB;

		if ( listA.hasCorrespondingInterestPoints() )
			corrListA = listA.getCorrespondingInterestPointsCopy();
		else
			corrListA = new ArrayList<>();

		if ( listB.hasCorrespondingInterestPoints() )
			corrListB = listA.getCorrespondingInterestPointsCopy();
		else
			corrListB = new ArrayList<>();

		for ( final PointMatchGeneric< I > pm : correspondences )
		{
			final I pA = pm.getPoint1();
			final I pB = pm.getPoint2();

			final CorrespondingInterestPoints correspondingToA = new CorrespondingInterestPoints( pA.getId(), viewIdB, labelB, pB.getId() );
			final CorrespondingInterestPoints correspondingToB = new CorrespondingInterestPoints( pB.getId(), viewIdA, labelA, pA.getId() );

			corrListA.add( correspondingToA );
			corrListB.add( correspondingToB );
		}

		listA.setCorrespondingInterestPoints( corrListA );
		listB.setCorrespondingInterestPoints( corrListB );
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
