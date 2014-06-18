package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.plugin.Interest_Point_Registration.RegistrationType;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.ChannelProcess;
import spim.process.interestpointregistration.optimizationtypes.GlobalOptimizationType;

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
	private int inputTransform = 0;
	
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
	 * @param registrationType - which kind of registration
	 * @return
	 */
	public abstract boolean register( final GlobalOptimizationType registrationType );

	/**
	 * adds the questions this registration wants to ask
	 * 
	 * @param gd
	 * @param registrationType - which kind of registration
	 */
	public abstract void addQuery( final GenericDialog gd, final RegistrationType registrationType );
	
	/**
	 * queries the questions asked before
	 * 
	 * @param gd
	 * @param registrationType - which kind of timeseries registration
	 * @return
	 */
	public abstract boolean parseDialog( final GenericDialog gd, final RegistrationType registrationType );
	
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
	 * @return inputTransform type of input transform ( 0 == calibration, 1 == current transform, including calibration )
	 */
	public int getInitialTransformType() { return inputTransform; }
	
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
		
		final ArrayList< Channel > channels = new ArrayList< Channel >();
		for ( final ChannelProcess c : channelsToProcess )
			channels.add( c.getChannel() );
		
		final double minResolution = Apply_Transformation.assembleAllMetaData( spimData.getSequenceDescription(), timepointsToProcess, channels, illumsToProcess, anglesToProcess );
		
		if ( Double.isNaN( minResolution ) )
			return false;
		
		setMinResolution( minResolution );

		// try to set the unit as well
		for ( final TimePoint t : timepointsToProcess )
			for ( final ChannelProcess c : channelsToProcess )
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						// bureaucracy
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c.getChannel(), a, i );
						
						final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );

						if ( !viewDescription.isPresent() )
							continue;

						if ( viewDescription.getViewSetup().hasVoxelSize() )
						{
							final String unit = viewDescription.getViewSetup().getVoxelSize().unit();
							if ( !( unit == null || unit.isEmpty() ) )
							setUnit( unit );
						}
					}
		
		return true;
	}
}
