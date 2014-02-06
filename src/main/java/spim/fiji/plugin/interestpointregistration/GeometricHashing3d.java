package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.RigidModel3D;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public class GeometricHashing3d extends InterestPointRegistration
{
	final String modelChoice[] = new String[] { "Translation", "Rigid", "Affine" };
	public static int defaultModel = 2;	
	protected int model = 2;

	public GeometricHashing3d( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		super( spimData, timepointsToProcess, channelsToProcess );
	}

	@Override
	public boolean register( final boolean isTimeSeriesRegistration )
	{
		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assembling metadata for all views involved" );

		if ( !assembleAllMetaData() )
		{
			IOFunctions.println( "Could not assemble metadata. Stopping." );
			return false;
		}
		
		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assembling metadata done, resolution of world coordinates is " + min + " " + unit + "/px."  );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Starting registration" );

		for ( final TimePoint timepoint : timepointsToProcess )
		{
			final ArrayList< PairOfInterestPointLists > pairs = this.getAllViewPairs( timepoint );
			
			final ExecutorService taskExecutor = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() );
			final ArrayList< GeometricHashing3dPairwise > tasks = new ArrayList< GeometricHashing3dPairwise >(); // your tasks
			
			for ( final PairOfInterestPointLists pair : pairs )
			{
				// just for logging the names and results of pairwise comparison
				final ViewDescription<TimePoint, ViewSetup> viewA = spimData.getSequenceDescription().getViewDescription( pair.getViewIdA() );
		    	final ViewDescription<TimePoint, ViewSetup> viewB = spimData.getSequenceDescription().getViewDescription( pair.getViewIdB() );
		    	
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
			
			int sumCandidates = 0;
			int sumInliers = 0;
			for ( final PairOfInterestPointLists pair : pairs )
			{
				sumCandidates += pair.getCandidates().size();
				sumInliers += pair.getInliers().size();
			}
			
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Number of Candidates: " + sumCandidates );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Number of Inliers: " + sumInliers );			
			
    		if ( model == 0 )
    			computeGlobalOpt( new TranslationModel3D(), pairs, timepoint );
    		else if ( model == 1 )
    			computeGlobalOpt( new RigidModel3D(), pairs, timepoint );
    		else
    			computeGlobalOpt( new AffineModel3D(), pairs, timepoint );	
		}
		
		return true;
	}
	
	protected < M extends Model< M > > void computeGlobalOpt( final M model, final ArrayList< PairOfInterestPointLists > pairs, final TimePoint timepoint )
	{
		// a sorted list of all views
		final HashMap< ViewId, List< InterestPoint > > pointLists = this.getInterestPoints( timepoint );
		final ArrayList< ViewId > views = new ArrayList< ViewId >();
		views.addAll( pointLists.keySet() );
		Collections.sort( views );

		final HashMap< ViewId, Tile< M > > tiles = GlobalOpt.globalOptimization( model, views, pairs );
		final ViewRegistrations viewRegistrations = spimData.getViewRegistrations();

		String channelList = "[";
		for ( final ChannelProcess c : channelsToProcess )
			channelList += c.getLabel() + " (c=" + c.getChannel().getName() + "), ";
		channelList = channelList.substring( 0, channelList.length() - 2 ) + "]";
		
		// update the view registrations
		for ( final ViewId viewId : views )
		{
			final Tile< M > tile = tiles.get( viewId );
			final AbstractAffineModel3D<?> tilemodel = (AbstractAffineModel3D<?>)tile.getModel();
			final float[] m = tilemodel.getMatrix( null );
			final ViewRegistration vr = viewRegistrations.getViewRegistration( viewId );
			
			final AffineTransform3D t = new AffineTransform3D();
			t.set( m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], m[10], m[11] );
			final ViewTransform vt = new ViewTransformAffine( "Geometric Hasing on " + channelList, t );
			vr.preconcatenateTransform( vt );
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
