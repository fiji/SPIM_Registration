package spim.fiji.plugin.interestpointregistration;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.spimdata.SpimData2;
import ij.gui.GenericDialog;

public class GeometricHashing3d extends InterestPointRegistration
{
	final String modelChoice[] = new String[] { "Translation", "Rigid", "Affine" };
	public static int defaultModel = 2;

	protected int model = 2;

	public GeometricHashing3d( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		super( spimData, timepointsToProcess, channelsToProcess );
	}
	
	@Override
	public GeometricHashing3d newInstance( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		return new GeometricHashing3d( spimData, timepointsToProcess, channelsToProcess );
	}

	@Override
	public String getDescription() { return "Fast 3d geometric hashing (rotation invariant)";}

	@Override
	public void addQuery( final GenericDialog gd, final boolean isTimeSeriesRegistration )
	{
		gd.addChoice( "Transformation model", modelChoice, modelChoice[ defaultModel ] );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd, final boolean isTimeSeriesRegistration )
	{
		model = defaultModel = gd.getNextChoiceIndex();
		return true;
	}
}
