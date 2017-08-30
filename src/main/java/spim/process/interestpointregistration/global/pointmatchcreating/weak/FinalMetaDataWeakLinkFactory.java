package spim.process.interestpointregistration.global.pointmatchcreating.weak;

import java.util.HashMap;
import java.util.Map;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import spim.process.interestpointregistration.pairwise.constellation.overlap.OverlapDetection;

public class FinalMetaDataWeakLinkFactory implements WeakLinkFactory
{
	final OverlapDetection< ViewId > overlapDetection;
	final Map< ViewId, ViewRegistration > viewRegistrations;

	public FinalMetaDataWeakLinkFactory(
			final OverlapDetection< ViewId > overlapDetection,
			final Map< ViewId, ViewRegistration > viewRegistrations )
	{
		this.viewRegistrations = viewRegistrations;
		this.overlapDetection = overlapDetection;
	}

	@Override
	public < M extends Model< M > > WeakLinkPointMatchCreator< M > create(
			final HashMap< ViewId, Tile< M > > models )
	{
		return new FinalMetaDataWeakLinkCreator<>( models, overlapDetection, viewRegistrations );
	}

}
