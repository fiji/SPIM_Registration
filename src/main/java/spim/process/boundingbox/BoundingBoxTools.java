package spim.process.boundingbox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;

public class BoundingBoxTools
{
	public static < V extends ViewId > BoundingBox maximalBoundingBox( final SpimData2 spimData, final Collection< V > viewIds, final String title )
	{
		// filter not present ViewIds
		final List< V > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		return new BoundingBoxMaximal( viewIds, spimData ).estimate( title );
	}

	public static List< BoundingBox > getAllBoundingBoxes( final SpimData2 spimData, final Collection< ViewId > currentlySelected, final boolean addBoundingBoxForAllViews )
	{
		final List< BoundingBox > bbs = new ArrayList<>();
		bbs.addAll( spimData.getBoundingBoxes().getBoundingBoxes() );

		final ArrayList< ViewId > allViews = new ArrayList<>();

		if ( currentlySelected != null && currentlySelected.size() > 0 )
		{
			allViews.addAll( currentlySelected );
			bbs.add( BoundingBoxTools.maximalBoundingBox( spimData, allViews, "Currently Selected Views" ) );
		}

		if ( addBoundingBoxForAllViews )
		{
			allViews.clear();
			allViews.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );
			bbs.add( BoundingBoxTools.maximalBoundingBox( spimData, allViews, "All Views" ) );
		}

		return bbs;
	}

}
