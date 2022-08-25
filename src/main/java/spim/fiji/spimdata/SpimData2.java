/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2022 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package spim.fiji.spimdata;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

/**
 * Extends the {@link SpimData} class; has additonally detections
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class SpimData2 extends SpimData
{
	private ViewInterestPoints viewsInterestPoints;
	private BoundingBoxes boundingBoxes;
	
	public SpimData2(
			final File basePath,
			final SequenceDescription sequenceDescription,
			final ViewRegistrations viewRegistrations,
			final ViewInterestPoints viewsInterestPoints,
			final BoundingBoxes boundingBoxes )
	{
		super( basePath, sequenceDescription, viewRegistrations );

		this.viewsInterestPoints = viewsInterestPoints;
		this.boundingBoxes = boundingBoxes;
	}

	protected SpimData2()
	{}

	public ViewInterestPoints getViewInterestPoints() { return viewsInterestPoints; }
	public BoundingBoxes getBoundingBoxes() { return boundingBoxes; }

	protected void setViewsInterestPoints( final ViewInterestPoints viewsInterestPoints )
	{
		this.viewsInterestPoints = viewsInterestPoints;
	}

	protected void setBoundingBoxes( final BoundingBoxes boundingBoxes )
	{
		this.boundingBoxes = boundingBoxes;
	}

	/**
	 * @param seqDesc
	 * @param t
	 * @param c
	 * @param a
	 * @param i
	 * @return - the ViewId that fits to timepoint, angle, channel &amp; illumination by ID (or null if it does not exist)
	 */
	public static ViewId getViewId( final SequenceDescription seqDesc, final TimePoint t, final Channel c, final Angle a, final Illumination i )
	{
		final ViewSetup viewSetup = getViewSetup( seqDesc.getViewSetupsOrdered(), c, a, i );
		
		if ( viewSetup == null )
			return null;
		else
			return new ViewId( t.getId(), viewSetup.getId() );
	}

	public static ViewSetup getViewSetup( final List< ? extends ViewSetup > list, final Channel c, final Angle a, final Illumination i )
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

	public static ArrayList< ViewSetup > getAllViewSetupsSorted( final SpimData data, final List< ? extends ViewId > viewIds )
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

	public static ArrayList< ViewId > getAllViewIdsSorted( final SpimData data, final List< ? extends ViewSetup > setups, final List< ? extends TimePoint > tps )
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

	public static ArrayList< Angle > getAllAnglesForChannelTimepointSorted( final SpimData data, final Collection< ? extends ViewId > viewIds, final Channel c, final TimePoint t )
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

	public static ArrayList< Angle > getAllAnglesSorted( final SpimData data, final Collection< ? extends ViewId > viewIds )
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

	public static ArrayList< Illumination > getAllIlluminationsForChannelTimepointSorted( final SpimData data, final Collection< ? extends ViewId > viewIds, final Channel c, final TimePoint t )
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

	public static ArrayList< Illumination > getAllIlluminationsSorted( final SpimData data, final Collection< ? extends ViewId > viewIds )
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

	public static ArrayList< Channel > getAllChannelsSorted( final SpimData data, final Collection< ? extends ViewId > viewIds )
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

	public static ArrayList< ViewDescription > getAllViewIdsForChannelSorted( final SpimData data, final Collection< ? extends ViewId > viewIds, final Channel channel )
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

	public static ArrayList< ViewDescription > getAllViewIdsForChannelTimePointSorted( final SpimData data, final Collection< ? extends ViewId > viewIds, final Channel channel, final TimePoint timePoint )
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

	public static ArrayList< ViewDescription > getAllViewIdsForTimePointSorted( final SpimData data, final Collection< ? extends ViewId > viewIds, final TimePoint timepoint )
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

	public static ArrayList< ViewSetup> getAllViewSetups( final Collection< ? extends ViewDescription >  vds )
	{
		final HashSet< ViewSetup > set = new HashSet< ViewSetup >();

		for ( final ViewDescription vd : vds )
			if ( vd.isPresent() )
				set.add( vd.getViewSetup() );

		final ArrayList< ViewSetup > setups = new ArrayList< ViewSetup >();
		setups.addAll( set );
		Collections.sort( setups );

		return setups;
	}

	public static ArrayList< TimePoint > getAllTimePointsSorted( final SpimData data, final Collection< ? extends ViewId > viewIds )
	{
		final ArrayList< ViewDescription > vds = new ArrayList< ViewDescription >();

		for ( final ViewId v : viewIds )
			vds.add( data.getSequenceDescription().getViewDescription( v ) );

		return getAllTimePointsSorted( vds );
	}

	public static ArrayList< TimePoint > getAllTimePointsSorted( final Collection< ? extends ViewDescription > vds )
	{
		final HashSet< TimePoint > timepointSet = new HashSet< TimePoint >();

		for ( final ViewDescription vd : vds )
			if ( vd.isPresent() )
				timepointSet.add( vd.getTimePoint() );

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

	public static String saveXML( final SpimData2 data, final String xmlFileName, final String clusterExtension  )
	{
		// save the xml
		final XmlIoSpimData2 io = new XmlIoSpimData2( clusterExtension );
		
		final String xml = new File( data.getBasePath(), new File( xmlFileName ).getName() ).getAbsolutePath();
		try 
		{
			io.save( data, xml );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + io.lastFileName() + "'." );
			return xml;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + io.lastFileName() + "': " + e );
			e.printStackTrace();
			return null;
		}
	}
}
