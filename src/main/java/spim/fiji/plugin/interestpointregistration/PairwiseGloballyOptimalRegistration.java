package spim.fiji.plugin.interestpointregistration;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;

public abstract class PairwiseGloballyOptimalRegistration< T extends Callable< ChannelInterestPointListPair > > extends InterestPointRegistration
{
	public PairwiseGloballyOptimalRegistration(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}
	
	protected abstract T getPairwiseMatching( final ChannelInterestPointListPair pair, final String description );
	
	/**
	 * @param subset
	 * @param registrationType
	 * @param spimData
	 * @param channelsToProcess
	 * @param considerTimePointsAsUnit
	 * @return - true if the global optimization could be run successfully, otherwise false (XML will not be saved if false)
	 */
	protected abstract boolean runGlobalOpt(
			final GlobalOptimizationSubset subset, 
			final GlobalOptimizationType registrationType,
			final SpimData2 spimData,
			final List< ChannelProcess > channelsToProcess,
			final boolean considerTimePointsAsUnit );

	@Override
	public boolean register( final GlobalOptimizationType registrationType )
	{
		final SpimData2 spimData = getSpimData();

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Starting registration" );

		// get a list of all pairs for this specific GlobalOptimizationType
		final List< GlobalOptimizationSubset > list = registrationType.getAllViewPairs(
				spimData,
				getAnglesToProcess(),
				getChannelsToProcess(),
				getIllumsToProcess(),
				getTimepointsToProcess() );
		
		int successfulRuns = 0;
		
		for ( final GlobalOptimizationSubset subset : list )
		{
			final ArrayList< ChannelInterestPointListPair > pairs = subset.getViewPairs();
			
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
			final ArrayList< T > tasks = new ArrayList< T >(); // your tasks
			
			for ( final ChannelInterestPointListPair pair : pairs )
			{
				// just for logging the names and results of pairwise comparison
				final ViewDescription viewA = spimData.getSequenceDescription().getViewDescription( pair.getViewIdA() );
		    	final ViewDescription viewB = spimData.getSequenceDescription().getViewDescription( pair.getViewIdB() );
		    	
				final String description = "[TP=" + viewA.getTimePoint().getName() + 
		    			" angle=" + viewA.getViewSetup().getAngle().getName() + ", ch=" + viewA.getViewSetup().getChannel().getName() +
		    			", illum=" + viewA.getViewSetup().getIllumination().getName() + " >>> TP=" + viewB.getTimePoint().getName() +
		    			" angle=" + viewB.getViewSetup().getAngle().getName() + ", ch=" + viewB.getViewSetup().getChannel().getName() +
		    			", illum=" + viewB.getViewSetup().getIllumination().getName() + "]";
				
				tasks.add( getPairwiseMatching( pair, description ) );
			}
			try
			{
				// invokeAll() returns when all tasks are complete
				taskExecutor.invokeAll( tasks );
			}
			catch ( final InterruptedException e )
			{
				IOFunctions.println( "Failed to compute registrations for " + subset.getDescription() );
				e.printStackTrace();
			}
			
			
			// some statistics
			int sumCandidates = 0;
			int sumInliers = 0;
			for ( final ChannelInterestPointListPair pair : pairs )
			{
				sumCandidates += pair.getCandidates().size();
				sumInliers += pair.getInliers().size();
			}
			
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Number of Candidates: " + sumCandidates );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Number of Inliers: " + sumInliers );
			
			//
			// set and store correspondences
			//
			
			// first remove existing correspondences
			if ( registrationType.remove() )
				registrationType.clearExistingCorrespondences( spimData, getChannelsToProcess(), subset );

			// now add all corresponding interest points
			if ( registrationType.add() )
				registrationType.addCorrespondences( spimData, pairs );
			
			// save the files
			if ( registrationType.save() )
				registrationType.saveCorrespondences( spimData, getChannelsToProcess(), subset );
			
			if ( runGlobalOpt( subset, registrationType, spimData, getChannelsToProcess(), registrationType.considerTimePointsAsUnit() ) )
				++successfulRuns;
		}
		
		if ( successfulRuns > 0 )
			return true;
		else
			return false;
	}

}
