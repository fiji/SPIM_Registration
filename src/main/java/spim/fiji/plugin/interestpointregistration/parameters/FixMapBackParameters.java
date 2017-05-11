package spim.fiji.plugin.interestpointregistration.parameters;

import java.util.Map;
import java.util.Set;

import mpicbg.models.Model;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.Subset;

public class FixMapBackParameters
{
	public Set< ViewId > fixedViews;
	public Model< ? > model;
	public Map< Subset< ViewId >, ViewId > mapBackView;
}
