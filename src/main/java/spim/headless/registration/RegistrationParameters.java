package spim.headless.registration;

import java.util.Map;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;

public class RegistrationParameters
{
	/** the algorithm should modify the list viewTransform */

	Map< ViewId, ViewRegistration > registrations;
}
