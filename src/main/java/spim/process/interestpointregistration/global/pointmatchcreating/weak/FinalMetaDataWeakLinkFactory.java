package spim.process.interestpointregistration.global.pointmatchcreating.weak;

import java.util.HashMap;

import mpicbg.models.Model;
import mpicbg.models.Tile;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ViewId;

public class FinalMetaDataWeakLinkFactory implements WeakLinkFactory
{
	final ViewRegistrations viewRegistrations;

	public FinalMetaDataWeakLinkFactory( final ViewRegistrations viewRegistrations )
	{
		this.viewRegistrations = viewRegistrations;
	}

	@Override
	public < M extends Model< M > > WeakLinkPointMatchCreator< M > create(
			final HashMap< ViewId, Tile< M > > models )
	{
		return new FinalMetaDataWeakLinkCreator<>( models, viewRegistrations );
	}

}
