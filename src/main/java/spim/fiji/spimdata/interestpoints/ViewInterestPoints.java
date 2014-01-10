package spim.fiji.spimdata.interestpoints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;

/**
 * A class that organizes all interest point detections of all {@link ViewDescription}s (which extend {@link ViewId})
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class ViewInterestPoints
{
	protected final HashMap< ViewId, ViewInterestPointLists > interestPointCollectionLookup;

	public ViewInterestPoints( final HashMap< ViewId, ViewInterestPointLists > interestPointCollectionLookup )
	{
		this.interestPointCollectionLookup = interestPointCollectionLookup;
	}

	public ViewInterestPoints( final Collection< ViewInterestPointLists > interestPointCollection )
	{
		this.interestPointCollectionLookup = new HashMap< ViewId, ViewInterestPointLists >();
		
		for ( final ViewInterestPointLists v : interestPointCollection )
			this.interestPointCollectionLookup.put( v, v );
	}
	
	public HashMap< ViewId, ViewInterestPointLists > getViewInterestPoints() { return interestPointCollectionLookup; }
	
	public ViewInterestPointLists getViewInterestPointLists( final int tp, final int setupId )
	{
		return getViewInterestPointLists( new ViewId( tp, setupId ) );
	}

	public ViewInterestPointLists getViewInterestPointLists( final ViewId viewId )
	{
		return interestPointCollectionLookup.get( viewId );
	}
	
	/**
	 * Assembles the {@link ViewInterestPoints} object consisting of a list of {@link ViewInterestPointLists} objects for all {@link ViewDescription}s that are present
	 * 
	 * @param viewDescriptionList
	 * @return
	 */
	public static ViewInterestPoints createViewInterestPoints( final HashMap< ViewId, ViewDescription< TimePoint, ViewSetup > > viewDescriptionList )
	{
		final ArrayList< ViewInterestPointLists > viewInterestPointsList = new ArrayList< ViewInterestPointLists >();
		
		for ( final ViewDescription< TimePoint, ViewSetup > viewDescription : viewDescriptionList.values() )
			if ( viewDescription.isPresent() )
				viewInterestPointsList.add( new ViewInterestPointLists( viewDescription.getTimePointId(), viewDescription.getViewSetupId() ) );
		
		return new ViewInterestPoints( viewInterestPointsList );
	}
}
