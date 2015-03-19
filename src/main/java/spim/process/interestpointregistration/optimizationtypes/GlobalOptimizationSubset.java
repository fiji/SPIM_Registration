package spim.process.interestpointregistration.optimizationtypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mpicbg.models.AbstractModel;
import mpicbg.models.Affine3D;
import mpicbg.models.Model;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.GlobalOpt;
import spim.process.interestpointregistration.PairwiseMatch;

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
	final ArrayList< PairwiseMatch > viewPairs;
	final String description;
	
	// will be populated once getViews() is called
	ArrayList< ViewId > viewList;
	
	public GlobalOptimizationSubset( final ArrayList< PairwiseMatch > viewPairs, final String description )
	{
		this.viewPairs = viewPairs;
		this.description = description;
	}

	/**
	 * @param model
	 * @param type
	 * @param spimData
	 * @param channelsToProcess - just to annotate the registration
	 * @param description
	 * @return
	 */
	public < M extends Model< M > > boolean computeGlobalOpt(
			final M model,
			final GlobalOptimizationType type,
			final SpimData2 spimData,
			final List< ChannelProcess > channelsToProcess,
			final String description )
	{
		final HashMap< ViewId, Tile< M > > tiles = GlobalOpt.compute( model, type, this, type.considerTimePointsAsUnit() );

		if ( tiles == null )
			return false;
		
		String channelList = "[";
		for ( final ChannelProcess c : channelsToProcess )
			channelList += c.getLabel() + " (c=" + c.getChannel().getName() + "), ";
		channelList = channelList.substring( 0, channelList.length() - 2 ) + "]";

		final AffineTransform3D mapBackModel;
		
		// TODO: Map back first tile as good as possible to original location???
		if ( type.getMapBackReferenceTile( this ) != null && type.getMapBackModel() != null )
			mapBackModel = computeMapBackModel( tiles, type, spimData );
		else
			mapBackModel = null;

		// update the view registrations
		for ( final ViewId viewId : this.getViews() )
		{
			final Tile< M > tile = tiles.get( viewId );
			
			// TODO: we assume that M is an Affine3D, which is not necessarily true
			final Affine3D< ? > tilemodel = (Affine3D< ? >)tile.getModel();
			final double[][] m = new double[ 3 ][ 4 ];
			tilemodel.toMatrix( m );
			
			final AffineTransform3D t = new AffineTransform3D();
			t.set( m[0][0], m[0][1], m[0][2], m[0][3],
				   m[1][0], m[1][1], m[1][2], m[1][3],
				   m[2][0], m[2][1], m[2][2], m[2][3] );
			
			if ( mapBackModel != null )
			{
				t.preConcatenate( mapBackModel );
				IOFunctions.println( "ViewId=" + viewId.getViewSetupId() + ": " + t );
			}
			
			Apply_Transformation.preConcatenateTransform( spimData, viewId, t, description + " on " + channelList );
		}

		return true;
	}

	protected < M extends Model< M > > AffineTransform3D computeMapBackModel( final HashMap< ViewId, Tile< M > > tiles, final GlobalOptimizationType type, final SpimData2 spimData )
	{
		final AbstractModel< ? > mapBackModel = type.getMapBackModel();
		
		if ( mapBackModel.getMinNumMatches() > 4 )
		{
			IOFunctions.println( "Cannot map back using a model that needs more than 4 points: " + mapBackModel.getClass().getSimpleName() );

			return null;
		}
		else
		{
			IOFunctions.println( "Mapping back to reference frame using a " + mapBackModel.getClass().getSimpleName() );
			
			final ViewId referenceTile = type.getMapBackReferenceTile( this );
			final ViewDescription referenceTileViewDescription = spimData.getSequenceDescription().getViewDescription( referenceTile );
			final ViewSetup referenceTileSetup = referenceTileViewDescription.getViewSetup();
			Dimensions size = ViewSetupUtils.getSizeOrLoad( referenceTileSetup, referenceTileViewDescription.getTimePoint(), spimData.getSequenceDescription().getImgLoader() );
			long w = size.dimension( 0 );
			long h = size.dimension( 1 );

			final double[][] p = new double[][]{
					{ 0, 0, 0 },
					{ w, 0, 0 },
					{ 0, h, 0 },
					{ w, h, 0 } };

			// original coordinates == pa
			final double[][] pa = new double[ 4 ][ 3 ];
			
			// map coordinates to the actual input coordinates
			final ViewRegistration inputModel = spimData.getViewRegistrations().getViewRegistration( referenceTile );

			for ( int i = 0; i < p.length; ++i )
				inputModel.getModel().apply( p[ i ], pa[ i ] );
			
			final M outputModel = tiles.get( referenceTile ).getModel();
			
			// transformed coordinates == pb
			final double[][] pb = new double[ 4 ][ 3 ];

			for ( int i = 0; i < p.length; ++i )
				pb[ i ] = outputModel.apply( pa[ i ] );

			// compute the model that maps pb >> pa
			try
			{
				final ArrayList< PointMatch > pm = new ArrayList< PointMatch >();
				
				for ( int i = 0; i < p.length; ++i )
					pm.add( new PointMatch( new Point( pb[ i ] ), new Point( pa[ i ] ) ) );
				
				mapBackModel.fit( pm );
			} catch ( Exception e )
			{
				IOFunctions.println( "Could not compute model for mapping back: " + e );
				e.printStackTrace();
				return null;
			}

			final AffineTransform3D mapBack = new AffineTransform3D();
			final double[][] m = new double[ 3 ][ 4 ];
			((Affine3D<?>)mapBackModel).toMatrix( m );
			
			mapBack.set( m[0][0], m[0][1], m[0][2], + m[0][3],
						m[1][0], m[1][1], m[1][2], m[1][3], 
						m[2][0], m[2][1], m[2][2], m[2][3] );

			IOFunctions.println( "Model for mapping back: " + mapBack + "\n" );

			return mapBack;
		}
	}

	/**
	 * @return - all views that are part of the subset and will receive a transformation model
	 */
	public List< ViewId > getViews()
	{
		if ( viewList != null )
			return viewList;
		
		final Set< ViewId > viewSet = new HashSet< ViewId >();
		
		for ( final PairwiseMatch pair : getViewPairs() )
		{
			viewSet.add( pair.getViewIdA() );
			viewSet.add( pair.getViewIdB() );
		}
		
		viewList = new ArrayList< ViewId >();
		viewList.addAll( viewSet );
		Collections.sort( viewList );

		return viewList;
	}
	
	public List< PairwiseMatch > getViewPairs() { return viewPairs; }
	public String getDescription() { return description; }
}
