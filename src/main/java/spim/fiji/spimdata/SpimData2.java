package spim.fiji.spimdata;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
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

	public static ArrayList< ViewSetup > getAllViewSetupsSorted( final SpimData2 data, final List< ViewId > viewIds )
	{
		final HashSet< ViewSetup > setups = new HashSet< ViewSetup >();

		for ( final ViewId viewId : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( viewId );
			final ViewSetup setup = vd.getViewSetup();

			if ( vd.isPresent() )
				setups.add( setup );
		}

		final ArrayList< ViewSetup > setupList = new ArrayList< ViewSetup >();
		setupList.addAll( setups );
		Collections.sort( setupList );

		return setupList;
	}

	public static ArrayList< ViewId > getAllViewIdsSorted( final SpimData2 data, final List< ViewSetup > setups, final List< TimePoint > tps )
	{
		final ArrayList< ViewId > viewIds = new ArrayList< ViewId >();

		for ( final TimePoint tp : tps )
			for ( final ViewSetup vs : setups )
			{
				final ViewId v = new ViewId( tp.getId(), vs.getId() );
				final ViewDescription vd = data.getSequenceDescription().getViewDescription( v );
				
				if ( vd.isPresent() )
					viewIds.add( vd );
			}

		Collections.sort( viewIds );

		return viewIds;
	}

	public static ArrayList< Angle > getAllAnglesForChannelTimepointSorted( final SpimData2 data, final Collection< ViewId > viewIds, final Channel c, final TimePoint t )
	{
		final HashSet< Angle > angleSet = new HashSet< Angle >();

		for ( final ViewId v : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( v );
			
			if ( vd.isPresent() && v.getTimePointId() == t.getId() && vd.getViewSetup().getChannel().getId() == c.getId() )
				angleSet.add( vd.getViewSetup().getAngle() );
		}

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		angles.addAll( angleSet );
		Collections.sort( angles );

		return angles;
	}

	public static ArrayList< Angle > getAllAnglesSorted( final SpimData2 data, final Collection< ViewId > viewIds )
	{
		final HashSet< Angle > angleSet = new HashSet< Angle >();

		for ( final ViewId v : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( v );
			
			if ( vd.isPresent() )
				angleSet.add( vd.getViewSetup().getAngle() );
		}

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		angles.addAll( angleSet );
		Collections.sort( angles );

		return angles;
	}

	public static ArrayList< Illumination > getAllIlluminationsForChannelTimepointSorted( final SpimData2 data, final Collection< ViewId > viewIds, final Channel c, final TimePoint t )
	{
		final HashSet< Illumination > illumSet = new HashSet< Illumination >();

		for ( final ViewId v : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( v );
			
			if ( vd.isPresent() && v.getTimePointId() == t.getId() && vd.getViewSetup().getChannel().getId() == c.getId() )
				illumSet.add( vd.getViewSetup().getIllumination() );
		}

		final ArrayList< Illumination > illums = new ArrayList< Illumination >();
		illums.addAll( illumSet );
		Collections.sort( illums );

		return illums;
	}

	public static ArrayList< Illumination > getAllIlluminationsSorted( final SpimData2 data, final Collection< ViewId > viewIds )
	{
		final HashSet< Illumination > illumSet = new HashSet< Illumination >();

		for ( final ViewId v : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( v );
			
			if ( vd.isPresent() )
				illumSet.add( vd.getViewSetup().getIllumination() );
		}

		final ArrayList< Illumination > illums = new ArrayList< Illumination >();
		illums.addAll( illumSet );
		Collections.sort( illums );

		return illums;
	}

	public static ArrayList< Channel > getAllChannelsSorted( final SpimData2 data, final Collection< ViewId > viewIds )
	{
		final HashSet< Channel > channelSet = new HashSet< Channel >();

		for ( final ViewId v : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( v );
			
			if ( vd.isPresent() )
				channelSet.add( vd.getViewSetup().getChannel() );
		}

		final ArrayList< Channel > channels = new ArrayList< Channel >();
		channels.addAll( channelSet );
		Collections.sort( channels );

		return channels;
	}

	public static ArrayList< ViewDescription > getAllViewIdsForChannelSorted( final SpimData2 data, final Collection< ViewId > viewIds, final Channel channel )
	{
		final ArrayList< ViewDescription > views = new ArrayList< ViewDescription >();

		for ( final ViewId id : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( id );
			
			if ( vd.isPresent() && vd.getViewSetup().getChannel().getId() == channel.getId() )
				views.add( vd );
		}

		Collections.sort( views );

		return views;
	}

	public static ArrayList< ViewDescription > getAllViewIdsForChannelTimePointSorted( final SpimData2 data, final Collection< ViewId > viewIds, final Channel channel, final TimePoint timePoint )
	{
		final ArrayList< ViewDescription > views = new ArrayList< ViewDescription >();

		for ( final ViewId id : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( id );
			
			if ( vd.isPresent() && vd.getViewSetup().getChannel().getId() == channel.getId() && id.getTimePointId() == timePoint.getId() )
				views.add( vd );
		}

		Collections.sort( views );

		return views;
	}

	public static ArrayList< ViewDescription > getAllViewIdsForTimePointSorted( final SpimData2 data, final Collection< ViewId > viewIds, final TimePoint timepoint )
	{
		final ArrayList< ViewDescription > views = new ArrayList< ViewDescription >();

		for ( final ViewId id : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( id );
			
			if ( vd.isPresent() && vd.getTimePointId() == timepoint.getId() )
				views.add( vd );
		}

		Collections.sort( views );

		return views;
	}

	public static ArrayList< TimePoint > getAllTimePointsSorted( final SpimData2 data, final Collection< ViewId > viewIds )
	{
		final HashSet< TimePoint > timepointSet = new HashSet< TimePoint >();

		for ( final ViewId v : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( v );
			
			if ( vd.isPresent() )
				timepointSet.add( vd.getTimePoint() );
		}

		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >();
		timepoints.addAll( timepointSet );
		Collections.sort( timepoints,
				new Comparator< TimePoint >()
				{
					@Override
					public int compare( final TimePoint o1, final TimePoint o2 )
					{
						return o1.getId() - o2.getId();
					}
				});

		return timepoints;
	}
}
