package spim.process.interestpointregistration.global.linkremoval;

import mpicbg.models.TileConfiguration;

public interface LinkRemovalStrategy
{
	public boolean removeLink( TileConfiguration tc );
}
