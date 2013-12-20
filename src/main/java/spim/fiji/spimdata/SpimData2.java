package spim.fiji.spimdata;

import java.io.File;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import spim.fiji.spimdata.interestpoints.ViewsInterestPoints;

/**
 * Extends the {@link SpimData} class; has additonally detections
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class SpimData2 extends SpimData< TimePoint, ViewSetup >
{
	final protected ViewsInterestPoints viewsInterestPoints;
	
	public SpimData2( final File basePath, final SequenceDescription< TimePoint, ViewSetup > sequenceDescription, 
			final ViewRegistrations viewRegistrations, final ViewsInterestPoints viewsInterestPoints )
	{
		super( basePath, sequenceDescription, viewRegistrations );

		this.viewsInterestPoints = viewsInterestPoints;
	}

	public ViewsInterestPoints getViewsInterestPoints() { return viewsInterestPoints; }
	
	/**
	 * @param seqDesc
	 * @param t
	 * @param c
	 * @param a
	 * @param i
	 * @return - the ViewId that fits to timepoint, angle, channel & illumination by ID (or null if it does not exist)
	 */
	public static ViewId getViewId( final SequenceDescription<?, ?> seqDesc, final TimePoint t, final Channel c, final Angle a, final Illumination i )
	{
		for ( ViewSetup viewSetup : seqDesc.getViewSetups() )
		{
			if ( viewSetup.getAngle().getId() == a.getId() && 
				 viewSetup.getChannel().getId() == c.getId() && 
				 viewSetup.getIllumination().getId() == i.getId() )
			{
				return new ViewId( t.getId(), viewSetup.getId() );
			}
		}
		
		return null;
	}
}
