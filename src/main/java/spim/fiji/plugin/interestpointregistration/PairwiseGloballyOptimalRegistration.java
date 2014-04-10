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
import mpicbg.spim.data.sequence.ViewSetup;
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
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}
	
	protected abstract T getPairwiseMatching( final ChannelInterestPointListPair pair, final String description );
	protected abstract void runGlobalOpt(
			final GlobalOptimizationSubset subset, 
			final GlobalOptimizationType registrationType,
			final SpimData2 spimData,
			final ArrayList< ChannelProcess > channelsToProcess );

	@Override
	public boolean register( final GlobalOptimizationType registrationType )
	{
		final SpimData2 spimData = getSpimData();

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assembling metadata for all views involved in the registration" );

		if ( !assembleAllMetaData() )
		{
			IOFunctions.println( "Could not assemble metadata. Stopping." );
			return false;
		}
		
		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assembling metadata done, resolution of world coordinates is " + getMinResolution() + " " + getUnit() + "/px."  );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Starting registration" );

		// get a list of all pairs for this specific GlobalOptimizationType
		final List< GlobalOptimizationSubset > list = registrationType.getAllViewPairs(
				spimData,
				getAnglesToProcess(),
				getChannelsToProcess(),
				getIllumsToProcess(),
				getTimepointsToProcess(),
				inputTransform,
				getMinResolution() );
		
		for ( final GlobalOptimizationSubset subset : list )
		{
			final ArrayList< ChannelInterestPointListPair > pairs = subset.getViewPairs();
			
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
			final ArrayList< T > tasks = new ArrayList< T >(); // your tasks
			
			for ( final ChannelInterestPointListPair pair : pairs )
			{
				// just for logging the names and results of pairwise comparison
				final ViewDescription< TimePoint, ViewSetup > viewA = spimData.getSequenceDescription().getViewDescription( pair.getViewIdA() );
		    	final ViewDescription< TimePoint, ViewSetup > viewB = spimData.getSequenceDescription().getViewDescription( pair.getViewIdB() );
		    	
				final String description = "[TP=" + viewA.getTimePoint().getName() + 
		    			" angle=" + viewA.getViewSetup().getAngle().getName() + ", ch=" + viewA.getViewSetup().getChannel().getName() +
		    			", illum=" + viewA.getViewSetup().getIllumination().getName() + " >>> TP=" + viewB.getTimePoint().getName() +
		    			" angle=" + viewB.getViewSetup().getAngle().getName() + ", ch=" + viewB.getViewSetup().getChannel().getName() +
		    			", illum=" + viewB.getViewSetup().getIllumination().getName() + "]";
				
				tasks.add( getPairwiseMatching( pair, description ) );
				//new GeometricHashing3dPairwise( pair, model, comp, ransacParams )
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
			
			runGlobalOpt( subset, registrationType, spimData, getChannelsToProcess() );
		}
		
		return true;
	}

}
