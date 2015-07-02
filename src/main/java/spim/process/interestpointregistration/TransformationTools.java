package spim.process.interestpointregistration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;

public class TransformationTools
{
	/** call this method to load interestpoints and apply current transformation */
	public static Map< ViewId, List< InterestPoint > > getAllTransformedInterestPoints(
			final Collection< ViewId > viewIds,
			final Map< ViewId, ViewRegistration > registrations,
			final Map< ViewId, InterestPointList > interestpoints )
	{
		final HashMap< ViewId, List< InterestPoint > > transformedInterestpoints =
				new HashMap< ViewId, List< InterestPoint > >();

		for ( final ViewId viewId : viewIds )
			transformedInterestpoints.put( viewId, getTransformedInterestPoints( viewId, registrations, interestpoints ) );

		return transformedInterestpoints;
	}

	/** call this method to load interestpoints and apply current transformation */
	public static List< InterestPoint > getTransformedInterestPoints(
			final ViewId viewId,
			final Map< ViewId, ViewRegistration > registrations,
			final Map< ViewId, InterestPointList > interestpoints )
	{
		final List< InterestPoint > list = loadInterestPoints( interestpoints.get( viewId ) );
		final AffineTransform3D t = getTransform( viewId, registrations );

		return applyTransformation( list, t );
	}

	public static List< InterestPoint > loadInterestPoints( final InterestPointList list )
	{
		if ( list.getInterestPoints() == null )
			list.loadInterestPoints();

		return list.getInterestPoints();
	}

	public static AffineTransform3D getTransform( final ViewId viewId, final Map< ViewId, ViewRegistration > registrations )
	{
		final ViewRegistration r = registrations.get( viewId );
		r.updateModel();
		return r.getModel();
	}

	public static List< InterestPoint > applyTransformation( final List< InterestPoint > list, final AffineTransform3D m )
	{
		final ArrayList< InterestPoint > transformedList = new ArrayList< InterestPoint >();

		for ( final InterestPoint p : list )
		{
			final double[] l = new double[ 3 ];
			m.apply( p.getL(), l );
			
			transformedList.add( new InterestPoint( p.getId(), l ) );
		}

		return transformedList;
	}
}
