package spim.fiji.plugin.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.imglib2.realtransform.AffineTransform3D;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;

import spim.fiji.plugin.interestpointregistration.ChannelInterestPointListPair;
import spim.fiji.plugin.interestpointregistration.ChannelProcess;
import spim.fiji.plugin.interestpointregistration.GlobalOpt;
import spim.fiji.spimdata.SpimData2;

/**
 * Defines a subset of views that need to be matched and then a global optimization
 * be computed on.
 * 
 * In case of a registration of individual timepoints, each timepoint would be one
 * {@link GlobalOptimizationSubset}, for an all-to-all matching there is only one object.
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class GlobalOptimizationSubset
{
	final ArrayList< ChannelInterestPointListPair > viewPairs;
	final String description;
	
	// will be populated once getViews() is called
	ArrayList< ViewId > viewList;
	
	public GlobalOptimizationSubset( final ArrayList< ChannelInterestPointListPair > viewPairs, final String description )
	{
		this.viewPairs = viewPairs;
		this.description = description;
	}
	
	public < M extends Model< M > > void computeGlobalOpt(
			final M model,
			final GlobalOptimizationType type,
			final SpimData2 spimData,
			final ArrayList< ChannelProcess > channelsToProcess )
	{
		final HashMap< ViewId, Tile< M > > tiles = GlobalOpt.globalOptimization( model, type, this );
		final ViewRegistrations viewRegistrations = spimData.getViewRegistrations();

		String channelList = "[";
		for ( final ChannelProcess c : channelsToProcess )
			channelList += c.getLabel() + " (c=" + c.getChannel().getName() + "), ";
		channelList = channelList.substring( 0, channelList.length() - 2 ) + "]";
		
		// update the view registrations
		for ( final ViewId viewId : this.getViews() )
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

	
	/**
	 * @return - all views that are part of the subset and will receive a transformation model
	 */
	public ArrayList< ViewId > getViews()
	{
		if ( viewList != null )
			return viewList;
		
		final Set< ViewId > viewSet = new HashSet< ViewId >();
		
		for ( final ChannelInterestPointListPair pair : getViewPairs() )
		{
			viewSet.add( pair.getViewIdA() );
			viewSet.add( pair.getViewIdB() );
		}
		
		viewList = new ArrayList< ViewId >();
		viewList.addAll( viewSet );
		Collections.sort( viewList );

		return viewList;
	}
	
	public ArrayList< ChannelInterestPointListPair > getViewPairs() { return viewPairs; }
	public String getDescription() { return description; }
}
