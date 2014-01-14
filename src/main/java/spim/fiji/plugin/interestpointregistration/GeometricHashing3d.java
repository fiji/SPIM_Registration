package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.models.AffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.RigidModel3D;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
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
	
    public static float max_epsilon = 5;
    public static float min_inlier_ratio = 0.1f;
    public static int numIterations = 1000;
    public static float minInlierFactor = 3f;

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
				tasks.add( new PairwiseRegistration( pair, timepoint ) );
			
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
			
			Tile< ? > a = new Tile( null );

		}
		
		return true;
	}
	
	public class PairwiseRegistration implements Callable< ListPair >
	{
		final ListPair pair;
		final TimePoint timepoint;
		
		public PairwiseRegistration( final ListPair pair, final TimePoint timepoint )
		{ 
			this.pair = pair;
			this.timepoint = timepoint;
		}
		
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
        	
    		pair.setNumCandidates( candidates.size() );
    		
        	// compute ransac and remove inconsistent candidates
        	final ArrayList< PointMatchGeneric< Detection > > correspondences = new ArrayList< PointMatchGeneric< Detection > >();

    		final Model<?> m;
    		
    		if ( model == 0 )
    			m = new TranslationModel3D();
    		else if ( model == 1 )
    			m = new RigidModel3D();
    		else
    			m = new AffineModel3D();
    		
    		String result = RANSAC.computeRANSAC( candidates, correspondences, m, max_epsilon, min_inlier_ratio, minInlierFactor, numIterations );

   			pair.setNumCorrespondences( correspondences.size() );
    		
        	final ViewDescription<TimePoint, ViewSetup> viewA = spimData.getSequenceDescription().getViewDescription( pair.getViewIdA() );
        	final ViewDescription<TimePoint, ViewSetup> viewB = spimData.getSequenceDescription().getViewDescription( pair.getViewIdB() );
        	
        	IOFunctions.println( "TP=" + timepoint.getName() + 
        			" (angle=" + viewA.getViewSetup().getAngle().getName() + ", ch=" + viewA.getViewSetup().getChannel().getName() +
        			", illum=" + viewA.getViewSetup().getIllumination().getName() + " >>> " +
        			"angle=" + viewB.getViewSetup().getAngle().getName() + ", ch=" + viewB.getViewSetup().getChannel().getName() +
        			", illum=" + viewB.getViewSetup().getIllumination().getName() + "): " +
        			result );
			
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
