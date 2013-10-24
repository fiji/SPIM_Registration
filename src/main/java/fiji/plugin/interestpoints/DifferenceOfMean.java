package fiji.plugin.interestpoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.models.Point;
import mpicbg.spim.data.sequence.ViewId;
import fiji.spimdata.SpimDataBeads;


public class DifferenceOfMean extends DifferenceOf
{
	@Override
	public String getDescription() { return "Difference-of-Mean (Integral image based)"; }

	@Override
	public DifferenceOfMean newInstance() { return new DifferenceOfMean(); }

	@Override
	public HashMap< ViewId, List<Point> > findInterestPoints( final SpimDataBeads spimData, final boolean[] channelIds, final ArrayList<Integer> timepointindices )
	{
		// TODO Auto-generated method stub
		return null;
	}
}
