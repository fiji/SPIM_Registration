package spim.process.boundingbox;

import java.util.Collection;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.boundingbox.BoundingBox;

public interface BoundingBoxEstimation
{
	public BoundingBox estimate( final Collection< ViewId > views, final String title );
}
