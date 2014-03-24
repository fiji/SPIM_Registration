package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.Tile;
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
import mpicbg.spim.mpicbg.PointMatchGeneric;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.plugin.interestpointregistration.optimizationtypes.GlobalOptimizationSubset;
import spim.fiji.plugin.interestpointregistration.optimizationtypes.GlobalOptimizationType;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public abstract class InterestPointRegistration
{
	final SpimData2 spimData1;
	final ArrayList< Angle > anglesToProcess1;
	final ArrayList< ChannelProcess > channelsToProcess1;
	final ArrayList< Illumination > illumsToProcess1;
	final ArrayList< TimePoint > timepointsToProcess1; 
	
	/*
	 * type of input transform ( 0 == calibration, 1 == current transform, including calibration )
	 */
	protected int inputTransform = 0;
	
	/*
	 * ensure that the resolution of the world coordinates corresponds to the finest resolution
	 * of any dimension, i.e. if the scaling is (x=0.73 / y=0.5 / z=2 ), one pixel will be 0.5um/px.
	 * 
	 * This only applies if the transformation is based on the calibration only!
	 */
	double min1 = Double.MAX_VALUE;
	String unit1 = "";
		
	public InterestPointRegistration(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		this.spimData1 = spimData;
		this.anglesToProcess1 = anglesToProcess;
		this.channelsToProcess1 = channelsToProcess;
		this.illumsToProcess1 = illumsToProcess;
		this.timepointsToProcess1 = timepointsToProcess;
	}
	
	/**
	 * Registers all timepoints
	 * 
	 * @param registrationType - which kind of timeseries registration
	 * @return
	 */
	public abstract boolean register( final GlobalOptimizationType registrationType );

	/**
	 * adds the questions this registration wants to ask
	 * 
	 * @param gd
	 * @param registrationType - which kind of timeseries registration
	 */
	public abstract void addQuery( final GenericDialog gd, final int registrationType );
	
	/**
	 * queries the questions asked before
	 * 
	 * @param gd
	 * @param registrationType - which kind of timeseries registration
	 * @return
	 */
	public abstract boolean parseDialog( final GenericDialog gd, final int registrationType );
	
	/**
	 * @return - a new instance without any special properties
	 */
	public abstract InterestPointRegistration newInstance(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< ChannelProcess > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();
	
	protected SpimData2 getSpimData() { return spimData1; }
	public ArrayList< Angle > getAnglesToProcess() { return anglesToProcess1; }
	public ArrayList< ChannelProcess > getChannelsToProcess() { return channelsToProcess1; }
	public ArrayList< Illumination > getIllumsToProcess() { return illumsToProcess1; }
	public ArrayList< TimePoint > getTimepointsToProcess() { return timepointsToProcess1; }
	protected double getMinResolution() { return min1; }
	protected String getUnit() { return unit1; }
	
	protected void setMinResolution( final double min ) { this.min1 = min; }
	protected void setUnit( final String unit ) { this.unit1 = unit; }
	
	/**
	 * @param inputTransform type of input transform ( 0 == calibration, 1 == current transform, including calibration )
	 */
	public void setInitialTransformType( final int inputTransform ) { this.inputTransform = inputTransform; }
		
	/**
	 * Should be called before registration to make sure all metadata is right
	 * 
	 * @return
	 */
	protected boolean assembleAllMetaData()
	{
		final SpimData2 spimData = getSpimData();
		final ArrayList< TimePoint > timepointsToProcess = getTimepointsToProcess(); 
		final ArrayList< ChannelProcess > channelsToProcess = getChannelsToProcess();
		final ArrayList< Angle > anglesToProcess = getAnglesToProcess();
		final ArrayList< Illumination > illumsToProcess = getIllumsToProcess();
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Angle a : anglesToProcess )
				for ( final Illumination i : illumsToProcess )
					for ( final ChannelProcess c : channelsToProcess )
				{
						// bureaucracy
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c.getChannel(), a, i );
						
						if ( viewId == null )
						{
							IOFunctions.println( "An error occured. Could not find the corresponding ViewSetup for timepoint: " + t.getId() + " angle: " + 
									a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
						
							return false;
						}
						
						final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );

						if ( !viewDescription.isPresent() )
							continue;
						
						// load metadata to update the registrations if required
						if ( inputTransform == 0 )
						{
							// only use calibration as defined in the metadata
							if ( !calibrationAvailable( viewDescription.getViewSetup() ) )
							{
								if ( !spimData.getSequenceDescription().getImgLoader().loadMetaData( viewDescription ) )
								{
									IOFunctions.println( "An error occured. Cannot load calibration for timepoint: " + t.getId() + " angle: " + 
											a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
									
									IOFunctions.println( "Quitting. Please set it manually when defining the dataset or by modifying the XML" );
									
									return false;
								}						
							}

							if ( !calibrationAvailable( viewDescription.getViewSetup() ) )
							{
								IOFunctions.println( "An error occured. No calibration available for timepoint: " + t.getId() + " angle: " + 
										a.getId() + " channel: " + c.getChannel().getId() + " illum: " + i.getId() );
								
								IOFunctions.println( "Quitting. Please set it manually when defining the dataset or by modifying the XML" );							
							}
							
							final double calX = viewDescription.getViewSetup().getPixelWidth();
							final double calY = viewDescription.getViewSetup().getPixelHeight();
							final double calZ = viewDescription.getViewSetup().getPixelDepth();
							
							setMinResolution( Math.min( getMinResolution(), calX ) );
							setMinResolution( Math.min( getMinResolution(), calY ) );
							setMinResolution( Math.min( getMinResolution(), calZ ) );
							
							if ( viewDescription.getViewSetup().getPixelSizeUnit() != null )
								setUnit( viewDescription.getViewSetup().getPixelSizeUnit() );
						}
				}
		
		return true;
	}


	protected boolean calibrationAvailable( final ViewSetup viewSetup )
	{
		if ( viewSetup.getPixelWidth() <= 0 || viewSetup.getPixelHeight() <= 0 || viewSetup.getPixelDepth() <= 0 )
			return false;
		else
			return true;
	}
	
	/**
	 * Add all correspondences the list for those that are compared here
	 * 
	 * @param pairs
	 */
	protected void addCorrespondences( final ArrayList< ChannelInterestPointListPair > pairs )
	{
		final SpimData2 spimData = getSpimData();

		for ( final ChannelInterestPointListPair pair : pairs )
		{
			final ArrayList< PointMatchGeneric< Detection > > correspondences = pair.getInliers();
			
			final String labelA = pair.getChannelProcessedA().getLabel();
			final String labelB = pair.getChannelProcessedB().getLabel();
			
			final ViewId viewA = pair.getViewIdA();
			final ViewId viewB = pair.getViewIdB();
			
			final InterestPointList listA = spimData.getViewInterestPoints().getViewInterestPointLists( viewA ).getInterestPointList( labelA );				
			final InterestPointList listB = spimData.getViewInterestPoints().getViewInterestPointLists( viewB ).getInterestPointList( labelB );
			
			final List< CorrespondingInterestPoints > corrListA = listA.getCorrespondingInterestPoints();
			final List< CorrespondingInterestPoints > corrListB = listB.getCorrespondingInterestPoints();
			
			for ( final PointMatchGeneric< Detection > d : correspondences )
			{
				final Detection dA = d.getPoint1();
				final Detection dB = d.getPoint2();
				
				final CorrespondingInterestPoints correspondingToA = new CorrespondingInterestPoints( dA.getId(), viewB, labelB, dB.getId() );
				final CorrespondingInterestPoints correspondingToB = new CorrespondingInterestPoints( dB.getId(), viewA, labelA, dA.getId() );
				
				corrListA.add( correspondingToA );
				corrListB.add( correspondingToB );
			}
		}		
	}
	
	/**
	 * Save all lists of existing correspondences for those that are compared here
	 * 
	 * @param set
	 */
	protected void saveCorrespondences( final GlobalOptimizationSubset set )
	{
		final SpimData2 spimData = getSpimData();
		final ArrayList< ChannelProcess > channelsToProcess = getChannelsToProcess();

		for ( final ViewId id : set.getViews() )
			for ( final ChannelProcess c : channelsToProcess )
				spimData.getViewInterestPoints().getViewInterestPointLists( id ).getInterestPointList( c.getLabel() ).saveCorrespondingInterestPoints();		
	}
	
	/**
	 * Clear all lists of existing correspondences for those that are compared here
	 * 
	 * @param set
	 */
	protected void clearExistingCorrespondences( final GlobalOptimizationSubset set )
	{
		final SpimData2 spimData = getSpimData();
		final ArrayList< ChannelProcess > channelsToProcess = getChannelsToProcess();

		for ( final ViewId id : set.getViews() )
			for ( final ChannelProcess c : channelsToProcess )
				spimData.getViewInterestPoints().getViewInterestPointLists( id ).getInterestPointList( c.getLabel() ).getCorrespondingInterestPoints().clear();		
	}
}
