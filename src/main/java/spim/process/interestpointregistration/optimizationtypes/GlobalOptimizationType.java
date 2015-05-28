package spim.process.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.models.AbstractModel;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.Detection;
import spim.process.interestpointregistration.MatchPointList;
import spim.process.interestpointregistration.PairwiseMatch;

/**
 * A certain type of global optimization, must be able to define all view pairs
 * that need to be matched and optimized inidivdually
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public abstract class GlobalOptimizationType
{
	final protected boolean considerTimePointsAsUnit;

	final SpimData2 spimData;
	final List< ViewId > viewIdsToProcess;
	final List< ChannelProcess > channelsToProcess;

	List< GlobalOptimizationSubset > subsets;

	Set< ViewId > fixedTiles;
	Map< GlobalOptimizationSubset, ViewId > referenceTiles;
	AbstractModel<?> mapBackModel;
	
	public GlobalOptimizationType(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final List< ChannelProcess > channelsToProcess,
			final boolean considerTimePointsAsUnit )
	{
		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
		this.channelsToProcess = channelsToProcess;

		this.considerTimePointsAsUnit = considerTimePointsAsUnit;

		this.fixedTiles = new HashSet< ViewId >();
		this.referenceTiles = new HashMap< GlobalOptimizationSubset, ViewId >();
		this.mapBackModel = null;

		// we cannot call assemble from the constructor as some subclasses might not
		// have set parameters passed by the constructor yet
		this.subsets = null;
	}

	/**
	 * @return - assembles the list of subsets and points for the registration
	 */
	protected abstract List< GlobalOptimizationSubset > assembleAllViewPairs();

	/**
	 * @return - the list of all subsets for the global optimization
	 */
	public List< GlobalOptimizationSubset > getAllViewPairs()
	{
		if ( subsets == null )
			this.subsets = assembleAllViewPairs();

		return subsets;
	}

	/**
	 * @param viewId
	 * @return - true if a certain tile is fixed, otherwise false
	 */
	public boolean isFixedTile( final ViewId viewId ) { return fixedTiles.contains( viewId ); }

	/**
	 * @return - the set of fixed tiles, can be empty
	 */
	public Set< ViewId > getFixedTiles() { return fixedTiles; }

	/**
	 * @param fixedTiles - the set of fixed tiles, can be empty, but not NULL
	 */
	public void setFixedTiles( final Set< ViewId > fixedTiles ) { this.fixedTiles = fixedTiles; }

	/**
	 * In case there is one tile which would be reference tile, return this one - can be null.
	 * This will be used to map the entire acquisition back to this frame as good as possible if the user
	 * asks for not fixing the first tile but still wants it roughly oriented like this
	 * 
	 * @param set - the current set that will be globally optimized
	 * @return
	 */
	public ViewId getMapBackReferenceTile( final GlobalOptimizationSubset set ) { return referenceTiles == null ? null : referenceTiles.get( set ); }

	/**
	 * @param set - for which subset
	 * @param referenceTile - set reference tile for mapping back
	 */
	public void setMapBackReferenceTile( final GlobalOptimizationSubset set, final ViewId referenceTile )
	{
		if ( this.referenceTiles == null )
			this.referenceTiles = new HashMap< GlobalOptimizationSubset, ViewId >();

		this.referenceTiles.put( set, referenceTile );
	}

	/**
	 * @param referenceTiles - set all reference tiles
	 */
	public void setMapBackReferenceTiles( final Map< GlobalOptimizationSubset, ViewId > referenceTiles ) { this.referenceTiles = referenceTiles; }

	/**
	 * The transformation model used to map back to the reference frame (can be null)
	 * 
	 * @return - a new instance of the model
	 */
	public AbstractModel<?> getMapBackModel() { return mapBackModel; }

	/**
	 * @param model - The transformation model used to map back to the reference frame (can be null)
	 */
	public void setMapBackModel( final AbstractModel<?> model ) { this.mapBackModel = model; }

	/** 
	 * @return - true if timepoints should be considered as one unit
	 */
	public boolean considerTimePointsAsUnit() { return considerTimePointsAsUnit; }

	public SpimData2 getSpimData() { return spimData; }

	/**
	 * Creates lists of input points for the registration, based on the current transformation of the views
	 * 
	 * Note: this always duplicates the location array from the input List&gt; InterestPoint &lt; !!!
	 * 
	 * @param timepoint
	 */
	protected HashMap< ViewId, MatchPointList > getInterestPoints( final TimePoint timepoint )
	{
		final HashMap< ViewId, MatchPointList > interestPoints = new HashMap< ViewId, MatchPointList >();
		final ViewRegistrations registrations = spimData.getViewRegistrations();
		final ViewInterestPoints interestpoints = spimData.getViewInterestPoints();
		
		for ( final ViewDescription vd : SpimData2.getAllViewIdsForTimePointSorted( spimData, viewIdsToProcess, timepoint) )
		{
			if ( !vd.isPresent() )
				continue;

			final ChannelProcess c = getChannelProcessForChannel( channelsToProcess, vd.getViewSetup().getChannel() );

			// no registration for this viewdescription
			if ( c == null )
				continue;

			final Angle a = vd.getViewSetup().getAngle();
			final Illumination i = vd.getViewSetup().getIllumination();

			// assemble a new list
			final ArrayList< InterestPoint > list = new ArrayList< InterestPoint >();

			// check the existing lists of points
			final ViewInterestPointLists lists = interestpoints.getViewInterestPointLists( vd );

			if ( !lists.contains( c.getLabel() ) )
			{
				IOFunctions.println( "Interest points for label '" + c.getLabel() + "' not found for timepoint: " + timepoint.getId() + " angle: " + 
						a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
				
				continue;
			}
			
			if ( lists.getInterestPointList( c.getLabel() ).getInterestPoints() == null )
			{
				if ( !lists.getInterestPointList( c.getLabel() ).loadInterestPoints() )
				{
					IOFunctions.println( "Interest points for label '" + c.getLabel() + "' could not be loaded for timepoint: " + timepoint.getId() + " angle: " + 
							a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
					
					continue;
				}
			}
			
			final List< InterestPoint > ptList = lists.getInterestPointList( c.getLabel() ).getInterestPoints();
			
			final ViewRegistration r = registrations.getViewRegistration( vd );
			r.updateModel();
			final AffineTransform3D m = r.getModel();
			
			for ( final InterestPoint p : ptList )
			{
				final double[] l = new double[ 3 ];
				m.apply( p.getL(), l );
				
				list.add( new InterestPoint( p.getId(), l ) );
			}
			
			interestPoints.put( vd, new MatchPointList( list, c ) );
		}

		return interestPoints;
	}

	protected static ChannelProcess getChannelProcessForChannel( final List< ChannelProcess > cpList, final Channel c )
	{
		for ( final ChannelProcess cp : cpList )
			if ( cp.getChannel().getId() == c.getId() )
				return cp;

		return null;
	}
	/**
	 * Add all correspondences the list for those that are compared here
	 * 
	 * This method can be overwritten if saving, adding &amp; clearing of correspondences is different for a certain type of registration
	 * 
	 * @param pairs
	 */
	public void addCorrespondences( final List< PairwiseMatch > pairs )
	{
		for ( final PairwiseMatch pair : pairs )
		{
			final ArrayList< PointMatchGeneric< Detection > > correspondences = pair.getInliers();

			final String labelA = pair.getChannelProcessedA().getLabel();
			final String labelB = pair.getChannelProcessedB().getLabel();

			final ViewId viewA = pair.getViewIdA();
			final ViewId viewB = pair.getViewIdB();

			final InterestPointList listA = spimData.getViewInterestPoints().getViewInterestPointLists( viewA ).getInterestPointList( labelA );				
			final InterestPointList listB = spimData.getViewInterestPoints().getViewInterestPointLists( viewB ).getInterestPointList( labelB );

			List< CorrespondingInterestPoints > corrListA = listA.getCorrespondingInterestPoints();
			List< CorrespondingInterestPoints > corrListB = listB.getCorrespondingInterestPoints();

			if ( corrListA == null )
				corrListA = new ArrayList< CorrespondingInterestPoints >();

			if ( corrListB == null )
				corrListB = new ArrayList< CorrespondingInterestPoints >();

			for ( final PointMatchGeneric< Detection > d : correspondences )
			{
				final Detection dA = d.getPoint1();
				final Detection dB = d.getPoint2();
				
				final CorrespondingInterestPoints correspondingToA = new CorrespondingInterestPoints( dA.getId(), viewB, labelB, dB.getId() );
				final CorrespondingInterestPoints correspondingToB = new CorrespondingInterestPoints( dB.getId(), viewA, labelA, dA.getId() );
				
				corrListA.add( correspondingToA );
				corrListB.add( correspondingToB );
			}

			listA.setCorrespondingInterestPoints( corrListA );
			listB.setCorrespondingInterestPoints( corrListB );
		}
	}

	/**
	 * Save all lists of existing correspondences for those that are compared here
	 * 
	 * This method can be overwritten if saving, adding &amp; clearing of correspondences is different for a certain type of registration
	 *
	 * @param set
	 */
	public void saveCorrespondences( final GlobalOptimizationSubset set )
	{
		for ( final ViewId id : set.getViews() )
			for ( final ChannelProcess c : channelsToProcess )
				if ( spimData.getSequenceDescription().getViewDescription( id ).getViewSetup().getChannel().getId() == c.getChannel().getId() )
					spimData.getViewInterestPoints().getViewInterestPointLists( id ).getInterestPointList( c.getLabel() ).saveCorrespondingInterestPoints();
	}

	/**
	 * Clear all lists of existing correspondences for those that are compared here
	 * 
	 * This method can be overwritten if saving, adding &amp; clearing of correspondences is different for a certain type of registration
	 *
	 * @param set
	 */
	public void clearExistingCorrespondences( final GlobalOptimizationSubset set )
	{
		for ( final ViewId id : set.getViews() )
			for ( final ChannelProcess c : channelsToProcess )
				if ( spimData.getSequenceDescription().getViewDescription( id ).getViewSetup().getChannel().getId() == c.getChannel().getId() )
					spimData.getViewInterestPoints().getViewInterestPointLists( id ).getInterestPointList( c.getLabel() ).setCorrespondingInterestPoints( new ArrayList< CorrespondingInterestPoints>() );
	}

	protected static boolean isValid( final ViewId viewId, final MatchPointList list )
	{
		if ( list == null )
		{
			IOFunctions.println( "Interest points NOT found for timepoint=" + viewId.getTimePointId() + ", viewsetup=" + viewId.getViewSetupId() );
			return false;
		}
		else
		{
			return true;
		}
	}
}
