package spim.process.boundingbox;

import java.util.Collection;
import java.util.HashMap;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalRealInterval;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class MaximumBoundingBox implements BoundingBoxEstimation
{
	final HashMap< ViewId, Dimensions > dimensions;
	final HashMap< ViewId, ViewRegistration > registrations;

	public MaximumBoundingBox( final AbstractSpimData< AbstractSequenceDescription<?,? extends BasicViewDescription< ? >, ? extends ImgLoader > > data )
	{
		this.dimensions = new HashMap<>();
		this.registrations = new HashMap<>();

		for ( final BasicViewDescription< ? > vd : data.getSequenceDescription().getViewDescriptions().values() )
		{
			if ( vd.isPresent() )
			{
				final Dimensions size = ViewSetupUtils.getSizeOrLoad( vd.getViewSetup(), vd.getTimePoint(), data.getSequenceDescription().getImgLoader() );
				dimensions.put( vd, size );
				registrations.put( vd, data.getViewRegistrations().getViewRegistration( vd ) );
			}
			else
			{
				IOFunctions.println( "Warning: ViewID"  + Group.pvid( vd ) + " is not present." );
			}
		}
	}

	public MaximumBoundingBox( final HashMap< ViewId, Dimensions > dimensions, final HashMap< ViewId, ViewRegistration > registrations )
	{
		this.dimensions = dimensions;
		this.registrations = registrations;
	}

	@Override
	public BoundingBox estimate( final Collection< ViewId > viewIds, final String title )
	{
		final double[] minBB = new double[ 3 ];
		final double[] maxBB = new double[ 3 ];

		for ( int d = 0; d < minBB.length; ++d )
		{
			minBB[ d ] = Double.MAX_VALUE;
			maxBB[ d ] = -Double.MAX_VALUE;
		}

		computeMaxBoundingBoxDimensions( viewIds, dimensions, registrations, minBB, maxBB );

		final BoundingBox maxsized = new BoundingBox( title, approximateLowerBound( minBB ), approximateUpperBound( maxBB ) );

		return maxsized;
	}

	public static int[] approximateLowerBound( final double[] min )
	{
		final int[] lowerBound = new int[ min.length ];

		for ( int d = 0; d < min.length; ++d )
			lowerBound[ d ] = (int)Math.round( Math.floor( min[ d ] ) );

		return lowerBound;
	}

	public static int[] approximateUpperBound( final double[] max )
	{
		final int[] upperBound = new int[ max.length ];

		for ( int d = 0; d < max.length; ++d )
			upperBound[ d ] = (int)Math.round( Math.ceil( max[ d ] ) );

		return upperBound;
	}

	/**
	 * @param spimData
	 * @param viewIdsToProcess
	 * @param minBB
	 * @param maxBB
	 */
	public static void computeMaxBoundingBoxDimensions(
			final Collection< ViewId > viewIds,
			final HashMap< ViewId, Dimensions > dimensions,
			final HashMap< ViewId, ViewRegistration > registrations,
			final double[] minBB, final double[] maxBB )
	{
		for ( int d = 0; d < minBB.length; ++d )
		{
			minBB[ d ] = Double.MAX_VALUE;
			maxBB[ d ] = -Double.MAX_VALUE;
		}

		for ( final ViewId viewId : viewIds )
		{
			if ( !dimensions.containsKey( viewId ) )
			{
				IOFunctions.println( "ERROR: ViewID"  + Group.pvid( viewId ) + " is not present in dimensions for MaximumBoundingBox.computeMaxBoundingBoxDimensions()" );
				continue;
			}

			if ( !registrations.containsKey( viewId ) )
			{
				IOFunctions.println( "ERROR: ViewID"  + Group.pvid( viewId ) + " is not present in registrations for MaximumBoundingBox.computeMaxBoundingBoxDimensions()" );
				continue;
			}

			final Dimensions size = dimensions.get( viewId );
			final double[] min = new double[]{ 0, 0, 0 };
			final double[] max = new double[]{
					size.dimension( 0 ) - 1,
					size.dimension( 1 ) - 1,
					size.dimension( 2 ) - 1 };
			
			final ViewRegistration r = registrations.get( viewId );
			r.updateModel();
			final FinalRealInterval interval = r.getModel().estimateBounds( new FinalRealInterval( min, max ) );
			
			for ( int d = 0; d < minBB.length; ++d )
			{
				minBB[ d ] = Math.min( minBB[ d ], interval.realMin( d ) );
				maxBB[ d ] = Math.max( maxBB[ d ], interval.realMax( d ) );
			}
		}
	}
}
