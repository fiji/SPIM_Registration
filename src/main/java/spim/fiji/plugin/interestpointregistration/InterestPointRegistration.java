package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

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
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public abstract class InterestPointRegistration< T extends Callable< ChannelInterestPointListPair > >
{
	final SpimData2 spimData1;
	final List< Angle > anglesToProcess1;
	final List< ChannelProcess > channelsToProcess1;
	final List< Illumination > illumsToProcess1;
	final List< TimePoint > timepointsToProcess1; 

	public InterestPointRegistration(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
	{
		this.spimData1 = spimData;
		this.anglesToProcess1 = anglesToProcess;
		this.channelsToProcess1 = channelsToProcess;
		this.illumsToProcess1 = illumsToProcess;
		this.timepointsToProcess1 = timepointsToProcess;
	}

	/**
	 * adds the questions this registration wants to ask
	 * 
	 * @param gd
	 * @param registrationType - which kind of registration
	 */
	public abstract void addQuery( final GenericDialog gd, final RegistrationType registrationType );
	
	/**
	 * queries the questions asked before
	 * 
	 * @param gd
	 * @param registrationType - which kind of timeseries registration
	 * @return
	 */
	public abstract boolean parseDialog( final GenericDialog gd, final RegistrationType registrationType );
	
	/**
	 * @return - a new instance without any special properties
	 */
	public abstract InterestPointRegistration<T> newInstance(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();

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


	protected SpimData2 getSpimData() { return spimData1; }
	public List< Angle > getAnglesToProcess() { return anglesToProcess1; }
	public List< ChannelProcess > getChannelsToProcess() { return channelsToProcess1; }
	public List< Illumination > getIllumsToProcess() { return illumsToProcess1; }
	public List< TimePoint > getTimepointsToProcess() { return timepointsToProcess1; }

	/**
	 * Registers all timepoints
	 * 
	 * @param registrationType - which kind of registration
	 * @return
	 */
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
