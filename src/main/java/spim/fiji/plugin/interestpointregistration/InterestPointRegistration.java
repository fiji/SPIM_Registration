package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.Threads;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.PairwiseMatch;
import spim.process.interestpointregistration.TransformationModel;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public abstract class InterestPointRegistration
{
	final SpimData2 spimData1;
	final List< ViewId > viewIdsToProcess;
	final List< ChannelProcess > channelsToProcess;

	List< List< PairwiseMatch > > statistics;

	/**
	 * Instantiate the interest point registration. It is performed for a spimdata object on a
	 * subset of angles, channels, illuminations and timepoints. Each channel is linked to a
	 * certain type of detections (e.g. beads, nuclei), hence the {@link ChannelProcess} object.
	 * 
	 * @param spimData
	 * @param viewIdsToProcess - which view id's to register
	 * @param channelsToProcess - which Channel uses which label for registration
	 */
	public InterestPointRegistration( final SpimData2 spimData, final List< ViewId > viewIdsToProcess, final List< ChannelProcess > channelsToProcess )
	{
		this.spimData1 = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
		this.channelsToProcess = channelsToProcess;
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
	public abstract InterestPointRegistration newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess, final List< ChannelProcess > channelsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();

	/**
	 * @param pair - which pair to compare
	 * @param description - a description String which pairs are compared
	 * @return - the object that will perform a pairwise matching and can return a result
	 */
	protected abstract Callable< PairwiseMatch > pairwiseMatchingInstance( final PairwiseMatch pair, final String description );

	/**
	 * @return - the transformation model to be used for the global optimization, and in most cases also for RANSAC
	 */
	protected abstract TransformationModel getTransformationModel();

	/**
	 * Run the global optimization on the subset
	 * 
	 * @param subset
	 * @param registrationType
	 * @return - true if the global optimization could be run successfully, otherwise false (XML will not be saved if false)
	 */
	@SuppressWarnings("unchecked")
	final protected boolean runGlobalOpt(
			final GlobalOptimizationSubset subset,
			final GlobalOptimizationType registrationType )
	{
		return subset.computeGlobalOpt(
				getTransformationModel().getModel(),
				registrationType,
				getSpimData(),
				getChannelsToProcess(),
				getDescription() + ", " + getTransformationModel().getDescription() );
	}

	protected SpimData2 getSpimData() { return spimData1; }
	public List< ViewId > getViewIdsToProcess() { return viewIdsToProcess; }
	public List< ChannelProcess > getChannelsToProcess() { return channelsToProcess; }
	public List< List< PairwiseMatch > > getStatistics() { return statistics; }

	/**
	 * Registers all timepoints. No matter which matching is done it is always the same principle.
	 * 
	 * First all pairwise correspondences are established, and then a global optimization is computed.
	 * The global optimization can is done in subsets, where the number of subsets &gt;= 1.
	 * 
	 * @param registrationType - which kind of registration
	 * @param save - if you want to save the correspondence files
	 * @return
	 */
	public boolean register( final GlobalOptimizationType registrationType, final boolean save, final boolean collectStatistics )
	{
		final SpimData2 spimData = getSpimData();

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Starting registration" );

		if ( collectStatistics )
			this.statistics = new ArrayList< List< PairwiseMatch > >();

		// get a list of all pairs for this specific GlobalOptimizationType
		final List< GlobalOptimizationSubset > list = registrationType.getAllViewPairs();

		int successfulRuns = 0;

		for ( final GlobalOptimizationSubset subset : list )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Finding correspondences for subset: " + subset.getDescription() );

			final List< PairwiseMatch > pairs = subset.getViewPairs();

			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
			final ArrayList< Callable< PairwiseMatch > > tasks = new ArrayList< Callable< PairwiseMatch > >(); // your tasks

			for ( final PairwiseMatch pair : pairs )
			{
				// just for logging the names and results of pairwise comparison
				final ViewDescription viewA = spimData.getSequenceDescription().getViewDescription( pair.getViewIdA() );
				final ViewDescription viewB = spimData.getSequenceDescription().getViewDescription( pair.getViewIdB() );

				final String description = "[TP=" + viewA.getTimePoint().getName() + 
						" angle=" + viewA.getViewSetup().getAngle().getName() + ", ch=" + viewA.getViewSetup().getChannel().getName() +
						", illum=" + viewA.getViewSetup().getIllumination().getName() + " >>> TP=" + viewB.getTimePoint().getName() +
						" angle=" + viewB.getViewSetup().getAngle().getName() + ", ch=" + viewB.getViewSetup().getChannel().getName() +
						", illum=" + viewB.getViewSetup().getIllumination().getName() + "]";
				
				tasks.add( pairwiseMatchingInstance( pair, description ) );
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
			for ( final PairwiseMatch pair : pairs )
			{
				sumCandidates += pair.getCandidates().size();
				sumInliers += pair.getInliers().size();
			}
			
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Number of Candidates: " + sumCandidates );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Number of Inliers: " + sumInliers );

			if ( collectStatistics )
				statistics.add( pairs );

			//
			// set and store correspondences
			//
			
			// first remove existing correspondences
			registrationType.clearExistingCorrespondences( subset );

			// now add all corresponding interest points
			registrationType.addCorrespondences( pairs );

			// save the files
			if ( save )
				registrationType.saveCorrespondences( subset );

			if ( runGlobalOpt( subset, registrationType ) )
				++successfulRuns;
		}
		
		if ( successfulRuns > 0 )
			return true;
		else
			return false;
	}
}
