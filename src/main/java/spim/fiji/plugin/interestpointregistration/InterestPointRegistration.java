package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public abstract class InterestPointRegistration
{
	final SpimData2 spimData;
	final ArrayList< TimePoint > timepointsToProcess; 
	final ArrayList< ChannelProcess > channelsToProcess;
	
	/*
	 * type of input transform ( 0 == calibration, 1 == current transform, including calibration )
	 */
	int inputTransform = 0;
	
	public InterestPointRegistration( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		this.spimData = spimData;
		this.timepointsToProcess = timepointsToProcess;
		this.channelsToProcess = channelsToProcess;
	}
	
	/**
	 * @param inputTransform type of input transform ( 0 == calibration, 1 == current transform, including calibration )
	 */
	public void setInitialTransformType( final int inputTransform ) { this.inputTransform = inputTransform; }
	
	/**
	 * @param timepoint
	 * @return - all pairs of views for a specific timepoint
	 */
	public ArrayList< ListPair > getAllViewPairs( final TimePoint timepoint )
	{
		final HashMap< ViewId, List< InterestPoint > > pointLists = this.getInterestPoints( timepoint );
		
		final ArrayList< ViewId > views = new ArrayList< ViewId >();
		views.addAll( pointLists.keySet() );
		Collections.sort( views );
		
		final ArrayList< ListPair > viewPairs = new ArrayList< ListPair >();
		
		for ( int a = 0; a < views.size() - 1; ++a )
			for ( int b = a + 1; b < views.size(); ++b )
			{
				final ViewId viewIdA = views.get( a );
				final ViewId viewIdB = views.get( b );
				
				final List< InterestPoint > listA = pointLists.get( viewIdA );
				final List< InterestPoint > listB = pointLists.get( viewIdB );
				
				viewPairs.add( new ListPair( viewIdA, viewIdB, listA, listB ) );
			}
		
		return viewPairs;
	}
	
	/**
	 * Creates lists of input points for the registration, depending if the input is the current transformation or just the calibration
	 * 
	 * @param timepoint
	 */
	protected HashMap< ViewId, List< InterestPoint > > getInterestPoints( final TimePoint timepoint )
	{
		final HashMap< ViewId, List< InterestPoint > > interestPoints = new HashMap< ViewId, List< InterestPoint > >();
		final ViewRegistrations registrations = spimData.getViewRegistrations();
		final ViewInterestPoints interestpoints = spimData.getViewInterestPoints();
		
		for ( final Angle a : spimData.getSequenceDescription().getAllAngles() )
			for ( final Illumination i : spimData.getSequenceDescription().getAllIlluminations() )
				for ( final ChannelProcess c : channelsToProcess )
			{
				// bureaucracy
				final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), timepoint, c.getChannel(), a, i );
				
				if ( viewId == null )
				{
					IOFunctions.println( "An error occured. Could not find the corresponding ViewSetup for timepoint: " + timepoint.getId() + " angle: " + 
							a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
				
					return null;
				}
				
				final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
						viewId.getTimePointId(), viewId.getViewSetupId() );

				if ( !viewDescription.isPresent() )
					continue;

				// update the registrations if required
				if ( inputTransform == 0 )
				{
					// only use calibration as defined in the metadata
					if ( !calibrationAvailable( viewDescription.getViewSetup() ) )
					{
						if ( !spimData.getSequenceDescription().getImgLoader().loadMetaData( viewDescription ) )
						{
							IOFunctions.println( "An error occured. Cannot load calibration for timepoint: " + timepoint.getId() + " angle: " + 
									a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
							
							IOFunctions.println( "Quitting. Please set it manually when defining the dataset or by modifying the XML" );
							
							return null;
						}						
					}

					if ( !calibrationAvailable( viewDescription.getViewSetup() ) )
					{
						IOFunctions.println( "An error occured. No calibration available for timepoint: " + timepoint.getId() + " angle: " + 
								a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
						
						IOFunctions.println( "Quitting. Please set it manually when defining the dataset or by modifying the XML" );							
					}
					
					final ViewRegistration r = registrations.getViewRegistration( viewId );
					r.identity();
					
					final double calX = viewDescription.getViewSetup().getPixelWidth();
					final double calY = viewDescription.getViewSetup().getPixelHeight();
					final double calZ = viewDescription.getViewSetup().getPixelDepth();
					
					final AffineTransform3D m = new AffineTransform3D();
					m.set( calX, 0.0f, 0.0f, 0.0f, 
						   0.0f, calY, 0.0f, 0.0f,
						   0.0f, 0.0f, calZ, 0.0f );
					final ViewTransform vt = new ViewTransformAffine( "calibration", m );
					r.preconcatenateTransform( vt );
				}

				// assemble a new list
				final ArrayList< InterestPoint > list = new ArrayList< InterestPoint >();

				// check the existing lists of points
				final ViewInterestPointLists lists = interestpoints.getViewInterestPointLists( viewId );

				if ( !lists.contains( c.getLabel() ) )
				{
					IOFunctions.println( "Interest points for label '' not found for timepoint: " + timepoint.getId() + " angle: " + 
							a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
					
					continue;
				}
				
				final List< InterestPoint > ptList = lists.getInterestPoints( c.getLabel() ).getInterestPointList();
				
				if ( ptList == null )
				{
					IOFunctions.println( "Interest points for label '' could not be loaded for timepoint: " + timepoint.getId() + " angle: " + 
							a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
					
					continue;					
				}
				
				final ViewRegistration r = registrations.getViewRegistration( viewId );
				final AffineTransform3D m = r.getModel();
				
				for ( final InterestPoint p : ptList )
				{
					final float[] l = new float[ 3 ];
					m.apply( p.getL(), l );
					
					list.add( new InterestPoint( p.getId(), l ) );
				}
				
				interestPoints.put( viewId, list );
			}
		
		return interestPoints;
	}
	
	protected boolean calibrationAvailable( final ViewSetup viewSetup )
	{
		if ( viewSetup.getPixelWidth() <= 0 || viewSetup.getPixelHeight() <= 0 || viewSetup.getPixelDepth() <= 0 )
			return false;
		else
			return true;
	}
	
	/**
	 * Registers all timepoints
	 * 
	 * @param isTimeSeriesRegistration
	 * @return
	 */
	public abstract boolean register( final boolean isTimeSeriesRegistration );

	/**
	 * adds the questions this registration wants to ask
	 * 
	 * @param gd
	 * @param isTimeSeriesRegistration
	 */
	public abstract void addQuery( final GenericDialog gd, final boolean isTimeSeriesRegistration );
	
	/**
	 * queries the questions asked before
	 * 
	 * @param gd
	 * @param isTimeSeriesRegistration
	 * @return
	 */
	public abstract boolean parseDialog( final GenericDialog gd, final boolean isTimeSeriesRegistration );
	
	/**
	 * @return - a new instance without any special properties
	 */
	public abstract InterestPointRegistration newInstance( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();
}
