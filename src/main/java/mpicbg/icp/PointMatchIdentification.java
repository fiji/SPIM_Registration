package mpicbg.icp;


import java.util.List;

import mpicbg.models.Point;
import mpicbg.pointdescriptor.exception.NoSuitablePointsException;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.RealLocalizable;

public interface PointMatchIdentification < P extends Point & RealLocalizable >
{
	public List< PointMatchGeneric< P > > assignPointMatches( final List< P > target, final List< P > reference ) throws NoSuitablePointsException;
}
