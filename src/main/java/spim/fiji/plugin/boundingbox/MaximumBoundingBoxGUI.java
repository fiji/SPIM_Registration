package spim.fiji.plugin.boundingbox;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;

public class MaximumBoundingBoxGUI extends BoundingBoxGUI
{

	public MaximumBoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

}
