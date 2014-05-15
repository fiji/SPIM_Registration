package spim.process.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import mpicbg.models.Affine3D;
import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelInterestPointListPair;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.GlobalOpt;

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
	
	public < M extends Model< M > > boolean computeGlobalOpt(
			final M model,
			final GlobalOptimizationType type,
			final SpimData2 spimData,
			final ArrayList< ChannelProcess > channelsToProcess,
			final String description,
			final boolean considerTimePointsAsUnit )
	{
		final HashMap< ViewId, Tile< M > > tiles = GlobalOpt.compute( model, type, this, considerTimePointsAsUnit );

		if ( tiles == null )
			return false;
		
		String channelList = "[";
		for ( final ChannelProcess c : channelsToProcess )
			channelList += c.getLabel() + " (c=" + c.getChannel().getName() + "), ";
		channelList = channelList.substring( 0, channelList.length() - 2 ) + "]";

		// update the view registrations
		for ( final ViewId viewId : this.getViews() )
		{
			final Tile< M > tile = tiles.get( viewId );
			
			// TODO: we assume that M is an Affine3D, which is not necessarily true
			final Affine3D< ? > tilemodel = (Affine3D< ? >)tile.getModel();
			final float[][] m = new float[ 3 ][ 4 ];
			tilemodel.toMatrix( m );
			
			final AffineTransform3D t = new AffineTransform3D();
			t.set( m[0][0], m[0][1], m[0][2], m[0][3],
				   m[1][0], m[1][1], m[1][2], m[1][3],
				   m[2][0], m[2][1], m[2][2], m[2][3] );
			
			Apply_Transformation.preConcatenateTransform( spimData, viewId, t, description + " on " + channelList );
		}
		
		return true;
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
