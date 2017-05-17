package spim.process.boundingbox;

import spim.fiji.spimdata.boundingbox.BoundingBox;

public interface BoundingBoxEstimation
{
	public BoundingBox estimate( final String title );
}
