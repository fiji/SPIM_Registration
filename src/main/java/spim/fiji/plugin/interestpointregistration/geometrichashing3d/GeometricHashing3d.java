package spim.fiji.plugin.interestpointregistration.geometrichashing3d;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.models.AffineModel3D;
import mpicbg.models.RigidModel3D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.interestpointregistration.ChannelInterestPointListPair;
import spim.fiji.plugin.interestpointregistration.ChannelProcess;
import spim.fiji.plugin.interestpointregistration.InterestPointRegistration;
import spim.fiji.spimdata.SpimData2;

public class GeometricHashing3d extends InterestPointRegistration
{
	final String modelChoice[] = new String[] { "Translation", "Rigid", "Affine" };
	public static int defaultModel = 2;	
	protected int model = 2;

	public GeometricHashing3d(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public boolean register( final boolean isTimeSeriesRegistration )
	{
		final SpimData2 spimData = getSpimData();
		final ArrayList< TimePoint > timepointsToProcess = getTimepointsToProcess(); 

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assembling metadata for all views involved" );

		if ( !assembleAllMetaData() )
		{
			IOFunctions.println( "Could not assemble metadata. Stopping." );
			return false;
		}
		
		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assembling metadata done, resolution of world coordinates is " + getMinResolution() + " " + getUnit() + "/px."  );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Starting registration" );

		for ( final TimePoint timepoint : timepointsToProcess )
		{
			final ArrayList< ChannelInterestPointListPair > pairs = this.getAllViewPairs( timepoint );
			
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
			final ArrayList< GeometricHashing3dPairwise > tasks = new ArrayList< GeometricHashing3dPairwise >(); // your tasks
			
			for ( final ChannelInterestPointListPair pair : pairs )
			{
				// just for logging the names and results of pairwise comparison
				final ViewDescription< TimePoint, ViewSetup > viewA = spimData.getSequenceDescription().getViewDescription( pair.getViewIdA() );
		    	final ViewDescription< TimePoint, ViewSetup > viewB = spimData.getSequenceDescription().getViewDescription( pair.getViewIdB() );
		    	
				final String comp = "[TP=" + viewA.getTimePoint().getName() + 
		    			" angle=" + viewA.getViewSetup().getAngle().getName() + ", ch=" + viewA.getViewSetup().getChannel().getName() +
		    			", illum=" + viewA.getViewSetup().getIllumination().getName() + " >>> TP=" + viewB.getTimePoint().getName() +
		    			" angle=" + viewB.getViewSetup().getAngle().getName() + ", ch=" + viewB.getViewSetup().getChannel().getName() +
		    			", illum=" + viewB.getViewSetup().getIllumination().getName() + "]";
				
				tasks.add( new GeometricHashing3dPairwise( pair, model, comp ) );
			}
			try
			{
				// invokeAll() returns when all tasks are complete
				taskExecutor.invokeAll( tasks );
			}
			catch ( final InterruptedException e )
			{
				IOFunctions.println( "Failed to compute registrations for timepoint: " + timepoint.getName() + "(id=" + timepoint.getId() + ")" );
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
			clearExistingCorrespondences( pairs );

			// now add all corresponding interest points
			addCorrespondences( pairs );
			
			// save the files
			saveCorrespondences( pairs );
			
    		if ( model == 0 )
    			computeGlobalOpt( new TranslationModel3D(), pairs, timepoint );
    		else if ( model == 1 )
    			computeGlobalOpt( new RigidModel3D(), pairs, timepoint );
    		else
    			computeGlobalOpt( new AffineModel3D(), pairs, timepoint );	
		}
		
		return true;
	}

	@Override
	public GeometricHashing3d newInstance(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		return new GeometricHashing3d( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Fast 3d geometric hashing";}

	@Override
	public void addQuery( final GenericDialog gd, final boolean isTimeSeriesRegistration )
	{
		gd.addChoice( "Transformation model", modelChoice, modelChoice[ defaultModel ] );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd, final boolean isTimeSeriesRegistration )
	{
		model = defaultModel = gd.getNextChoiceIndex();
		return true;
	}
}
