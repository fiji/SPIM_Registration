package fiji.plugin.interestpoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.models.Point;
import mpicbg.spim.data.sequence.ViewId;
import fiji.spimdata.SpimDataBeads;


public class DifferencOfGaussian extends DifferenceOf
{
	@Override
	public String getDescription() { return "Difference-of-Gaussian"; }

	@Override
	public DifferencOfGaussian newInstance() { return new DifferencOfGaussian(); }

	@Override
	public HashMap< ViewId, List<Point> > findInterestPoints( final SpimDataBeads spimData, final boolean[] channelIds, final ArrayList<Integer> timepointindices )
	{
		// TODO Auto-generated method stub
		return null;
	}
}
