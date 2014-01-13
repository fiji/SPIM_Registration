package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public class GeometricHashing3d extends InterestPointRegistration
{
	final String modelChoice[] = new String[] { "Translation", "Rigid", "Affine" };
	public static int defaultModel = 2;
	
	public static float differenceThreshold = 50; 
	public static float ratioOfDistance = 10; 
	public static boolean useAssociatedBeads = false;
	
	protected int model = 2;

	public GeometricHashing3d( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		super( spimData, timepointsToProcess, channelsToProcess );
	}

	@Override
	public boolean register( final boolean isTimeSeriesRegistration )
	{
		for ( final TimePoint timepoint : timepointsToProcess )
		{
			final ArrayList< ListPair > pairs = this.getAllViewPairs( timepoint );
			
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
			final ArrayList< PairwiseRegistration > tasks = new ArrayList< PairwiseRegistration >(); // your tasks
			
			for ( final ListPair pair : pairs )
				tasks.add( new PairwiseRegistration( pair ) );
			
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
		}
		
		return true;
	}
	
	public class PairwiseRegistration implements Callable< ListPair >
	{
		final ListPair pair;
		public PairwiseRegistration( final ListPair pair ) { this.pair = pair; }
		
		@Override
		public ListPair call() throws Exception 
		{
			final GeometricHasher hasher = new GeometricHasher();
			
			final ArrayList< Detection > listA = new ArrayList< Detection >();
			final ArrayList< Detection > listB = new ArrayList< Detection >();
			
			for ( final InterestPoint i : pair.getListA() )
				listA.add( new Detection( i.getId(), i.getL() ) );

			for ( final InterestPoint i : pair.getListB() )
				listB.add( new Detection( i.getId(), i.getL() ) );

    		final ArrayList< PointMatchGeneric< Detection > > candidates = hasher.extractCorrespondenceCandidates( 
    				listA, 
    				listB, 
    				differenceThreshold, 
    				ratioOfDistance, 
    				useAssociatedBeads );
        		
        	// compute ransac and remove inconsistent candidates
        	final ArrayList< PointMatchGeneric< Detection > > correspondences = new ArrayList< PointMatchGeneric< Detection > >();

        	IOFunctions.println( "Candiates (" + pair.getViewIdA().getViewSetupId() + ">" + pair.getViewIdB().getViewSetupId() + "): " + candidates.size() );
			
			return pair;
		}
	}

	@Override
	public GeometricHashing3d newInstance( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		return new GeometricHashing3d( spimData, timepointsToProcess, channelsToProcess );
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
