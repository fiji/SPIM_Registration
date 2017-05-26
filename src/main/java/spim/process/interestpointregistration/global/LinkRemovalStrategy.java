package spim.process.interestpointregistration.global;

import mpicbg.models.TileConfiguration;

public interface LinkRemovalStrategy
{
	public boolean removeLink( TileConfiguration tc );
}
