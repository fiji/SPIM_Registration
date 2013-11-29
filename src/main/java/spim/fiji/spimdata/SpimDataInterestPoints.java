package spim.fiji.spimdata;

import java.io.File;
import java.util.ArrayList;

import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;

public class SpimDataInterestPoints extends SpimData< TimePoint, ViewSetup >
{
	final protected ArrayList< ViewInterestPoints > viewBeads;
	
	public SpimDataInterestPoints( final File basePath, final SequenceDescription< TimePoint, ViewSetup > sequenceDescription, 
			final ViewRegistrations viewRegistrations, final ArrayList< ViewInterestPoints > viewBeads )
	{
		super( basePath, sequenceDescription, viewRegistrations );

		if ( viewBeads == null )
			this.viewBeads = new ArrayList< ViewInterestPoints >();
		else
			this.viewBeads = viewBeads;
	}

	public ArrayList< ViewInterestPoints > getViewInterestPoints() { return viewBeads; }
}
