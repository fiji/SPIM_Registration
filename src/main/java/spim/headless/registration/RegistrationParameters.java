package spim.headless.registration;

import java.util.List;
import java.util.Map;

import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.ViewId;

public class RegistrationParameters
{
	/** the algorithm should modify the list viewTransform */

	Map< ViewId, List< ViewTransform > > registration;
}
