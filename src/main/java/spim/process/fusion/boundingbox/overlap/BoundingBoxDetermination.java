package spim.process.fusion.boundingbox.overlap;

import java.util.Collection;

import spim.fiji.spimdata.boundingbox.BoundingBox;

public interface BoundingBoxDetermination<V>
{

	public BoundingBox getMaxBoundingBox(Collection< ? extends Collection< V > > viewGroups);
	public BoundingBox getMaxOverlapBoundingBox(Collection< ? extends Collection< V > > viewGroups);
	
	
}
