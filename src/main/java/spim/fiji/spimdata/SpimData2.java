package spim.fiji.spimdata;

import java.io.File;
import java.util.List;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

/**
 * Extends the {@link SpimData} class; has additonally detections
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class SpimData2 extends SpimData
{
	private ViewInterestPoints viewsInterestPoints;
	
	public SpimData2( final File basePath, final SequenceDescription sequenceDescription, 
			final ViewRegistrations viewRegistrations, final ViewInterestPoints viewsInterestPoints )
	{
		super( basePath, sequenceDescription, viewRegistrations );

		this.viewsInterestPoints = viewsInterestPoints;
	}
	
	protected SpimData2()
	{}

	public ViewInterestPoints getViewInterestPoints() { return viewsInterestPoints; }

	protected void setViewsInterestPoints( final ViewInterestPoints viewsInterestPoints )
	{
		this.viewsInterestPoints = viewsInterestPoints;
	}

	/**
	 * @param seqDesc
	 * @param t
	 * @param c
	 * @param a
	 * @param i
	 * @return - the ViewId that fits to timepoint, angle, channel & illumination by ID (or null if it does not exist)
	 */
	public static ViewId getViewId( final SequenceDescription seqDesc, final TimePoint t, final Channel c, final Angle a, final Illumination i )
	{
		final ViewSetup viewSetup = getViewSetup( seqDesc.getViewSetupsOrdered(), c, a, i );
		
		if ( viewSetup == null )
			return null;
		else
			return new ViewId( t.getId(), viewSetup.getId() );
	}

	public static ViewSetup getViewSetup( final List< ViewSetup > list, final Channel c, final Angle a, final Illumination i )
	{
		for ( final ViewSetup viewSetup : list )
		{
			if ( viewSetup.getAngle().getId() == a.getId() && 
				 viewSetup.getChannel().getId() == c.getId() && 
				 viewSetup.getIllumination().getId() == i.getId() )
			{
				return viewSetup;
			}
		}

		return null;
	}
}
