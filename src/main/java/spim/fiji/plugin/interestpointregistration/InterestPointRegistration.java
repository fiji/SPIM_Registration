package spim.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
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
	final List< Angle > anglesToProcess1;
	final List< ChannelProcess > channelsToProcess1;
	final List< Illumination > illumsToProcess1;
	final List< TimePoint > timepointsToProcess1; 

	/*
	 * ensure that the resolution of the world coordinates corresponds to the finest resolution
	 * of any dimension, i.e. if the scaling is (x=0.73 / y=0.5 / z=2 ), one pixel will be 0.5um/px.
	 * 
	 * This only applies if the transformation is based on the calibration only!
	 */
	//double min1 = Double.MAX_VALUE;
	//String unit1 = "";
		
	public InterestPointRegistration(
			final SpimData2 spimData,
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
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
			final List< Angle > anglesToProcess,
			final List< ChannelProcess > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();
	
	protected SpimData2 getSpimData() { return spimData1; }
	public List< Angle > getAnglesToProcess() { return anglesToProcess1; }
	public List< ChannelProcess > getChannelsToProcess() { return channelsToProcess1; }
	public List< Illumination > getIllumsToProcess() { return illumsToProcess1; }
	public List< TimePoint > getTimepointsToProcess() { return timepointsToProcess1; }
}
