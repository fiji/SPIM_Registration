package spim.headless.interestpointdetection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;

public class InterestPointTools
{
	public static void addInterestPoints( final SpimData2 data, final String label, final HashMap< ViewId, List< InterestPoint > > points )
	{
		addInterestPoints( data, label, points, "no parameters reported." );
	}

	public static void addInterestPoints( final SpimData2 data, final String label, final HashMap< ViewId, List< InterestPoint > > points, final String parameters )
	{
		for ( final ViewId viewId : points.keySet() )
		{
			final InterestPointList list =
					new InterestPointList(
							data.getBasePath(),
							new File( "interestpoints", "tpId_" + viewId.getTimePointId() +
									  "_viewSetupId_" + viewId.getViewSetupId() + "." + label ) );

			if ( parameters != null )
				list.setParameters( parameters );
			else
				list.setParameters( "" );

			list.setInterestPoints( points.get( viewId ) );

			list.saveInterestPoints();
			list.setCorrespondingInterestPoints( new ArrayList< CorrespondingInterestPoints >() );
			list.saveCorrespondingInterestPoints();

			final ViewInterestPointLists vipl = data.getViewInterestPoints().getViewInterestPointLists( viewId );
			vipl.addInterestPointList( label, list );
		}
	}

}
