package spim.fiji.spimdata.interestpoints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

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
public class ViewsInterestPoints
{
	protected final HashMap< ViewId, ViewInterestPointCollection > interestPointCollectionLookup;

	public ViewsInterestPoints( final HashMap< ViewId, ViewInterestPointCollection > interestPointCollectionLookup )
	{
		this.interestPointCollectionLookup = interestPointCollectionLookup;
	}

	public ViewsInterestPoints( final Collection< ViewInterestPointCollection > interestPointCollection )
	{
		this.interestPointCollectionLookup = new HashMap< ViewId, ViewInterestPointCollection >();
		
		for ( final ViewInterestPointCollection v : interestPointCollection )
			this.interestPointCollectionLookup.put( v, v );
	}
	
	public HashMap< ViewId, ViewInterestPointCollection > getViewInterestPoints() { return interestPointCollectionLookup; }
	
	public ViewInterestPointCollection getViewInterestPointCollection( final int tp, final int setupId )
	{
		return getViewInterestPointCollection( new ViewId( tp, setupId ) );
	}

	public ViewInterestPointCollection getViewInterestPointCollection( final ViewId viewId )
	{
		return interestPointCollectionLookup.get( viewId );
	}
	
	/**
	 * Assembles the {@link ViewsInterestPoints} object consisting of a list of {@link ViewInterestPointCollection} objects for all {@link ViewDescription}s that are present
	 * 
	 * @param viewDescriptionList
	 * @return
	 */
	public static ViewsInterestPoints createViewInterestPoints( final List< ViewDescription< TimePoint, ViewSetup > > viewDescriptionList )
	{
		final ArrayList< ViewInterestPointCollection > viewInterestPointsList = new ArrayList< ViewInterestPointCollection >();
		
		for ( final ViewDescription< TimePoint, ViewSetup > viewDescription : viewDescriptionList )
			if ( viewDescription.isPresent() )
				viewInterestPointsList.add( new ViewInterestPointCollection( viewDescription.getTimePointId(), viewDescription.getViewSetupId() ) );
		
		return new ViewsInterestPoints( viewInterestPointsList );
	}
}
