package spim.headless.interestpointdetection;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.InterestPointValue;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;

/**
 * The type Interest point tools.
 */
public class InterestPointTools
{
	public static String[] limitDetectionChoice = { "Brightest", "Around median (of those above threshold)", "Weakest (above threshold)" };	

	/**
	 * Add interest points. Does not save the InteresPoints
	 *
	 * @param data the data
	 * @param label the label
	 * @param points the points
	 * @return the true if successful
	 */
	public static boolean addInterestPoints( final SpimData2 data, final String label, final HashMap< ViewId, List< InterestPoint > > points )
	{
		return addInterestPoints( data, label, points, "no parameters reported." );
	}

	/**
	 * Add interest points.
	 *
	 * @param data the data
	 * @param label the label
	 * @param points the points
	 * @param parameters the parameters
	 * @return the true if successful, false if interest points cannot be saved
	 */
	public static boolean addInterestPoints( final SpimData2 data, final String label, final HashMap< ViewId, List< InterestPoint > > points, final String parameters )
	{
		for ( final ViewId viewId : points.keySet() )
		{
			final InterestPointList list =
					new InterestPointList(
							data.getBasePath(),
							new File(
									"interestpoints", "tpId_" + viewId.getTimePointId() +
									"_viewSetupId_" + viewId.getViewSetupId() + "." + label ) );

			if ( parameters != null )
				list.setParameters( parameters );
			else
				list.setParameters( "" );

			list.setInterestPoints( points.get( viewId ) );
			list.setCorrespondingInterestPoints( new ArrayList< CorrespondingInterestPoints >() );

			final ViewInterestPointLists vipl = data.getViewInterestPoints().getViewInterestPointLists( viewId );
			vipl.addInterestPointList( label, list );
		}

		return true;
	}

	public static List< InterestPoint > limitList( final int maxDetections, final int maxDetectionsTypeIndex, final List< InterestPoint > list )
	{
		if ( list.size() <= maxDetections )
		{
			return list;
		}
		else
		{
			if ( !InterestPointValue.class.isInstance( list.get( 0 ) ) )
			{
				IOFunctions.println( "ERROR: Cannot limit detections to " + maxDetections + ", wrong instance." );
				return list;
			}
			else
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Limiting detections to " + maxDetections + ", type = " + limitDetectionChoice[ maxDetectionsTypeIndex ] );

				Collections.sort( list, new Comparator< InterestPoint >()
				{

					@Override
					public int compare( final InterestPoint o1, final InterestPoint o2 )
					{
						final double v1 = Math.abs( ((InterestPointValue)o1).getIntensity() );
						final double v2 = Math.abs( ((InterestPointValue)o2).getIntensity() );

						if ( v1 < v2 )
							return 1;
						else if ( v1 == v2 )
							return 0;
						else
							return -1;
					}
				} );

				final ArrayList< InterestPoint > listNew = new ArrayList< InterestPoint >();

				if ( maxDetectionsTypeIndex == 0 )
				{
					// max
					for ( int i = 0; i < maxDetections; ++i )
						listNew.add( list.get( i ) );
				}
				else if ( maxDetectionsTypeIndex == 2 )
				{
					// min
					for ( int i = 0; i < maxDetections; ++i )
						listNew.add( list.get( list.size() - 1 - i ) );
				}
				else
				{
					// median
					final int median = list.size() / 2;
					
					IOFunctions.println( "Medium intensity: " + Math.abs( ((InterestPointValue)list.get( median )).getIntensity() ) );
					
					final int from = median - maxDetections/2;
					final int to = median + maxDetections/2;

					for ( int i = from; i <= to; ++i )
						listNew.add( list.get( list.size() - 1 - i ) );
				}
				return listNew;
			}
		}
	}

}
