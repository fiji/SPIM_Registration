package mpicbg.icp;


import java.util.List;

import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import fiji.util.node.Leaf;

public interface PointMatchIdentification < P extends Point & Leaf<P> >
{
	public List<PointMatch> assignPointMatches( final List<P> target, final List<P> reference ) throws NoSuitablePointsException;
}
