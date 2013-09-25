package fiji.spimdata;

import java.io.File;
import java.util.ArrayList;

import fiji.spimdata.beads.ViewBeads;
import fiji.spimdata.sequence.ViewSetupBeads;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;

public class SpimDataBeads extends SpimData< TimePoint, ViewSetupBeads >
{
	final protected ArrayList< ViewBeads > viewBeads;
	
	public SpimDataBeads( final File basePath, final SequenceDescription< TimePoint, ViewSetupBeads > sequenceDescription, 
			final ViewRegistrations viewRegistrations, final ArrayList< ViewBeads > viewBeads )
	{
		super( basePath, sequenceDescription, viewRegistrations );

		if ( viewBeads == null )
			this.viewBeads = new ArrayList< ViewBeads >();
		else
			this.viewBeads = viewBeads;
	}

	public ArrayList< ViewBeads > getViewBeads() { return viewBeads; }
}
