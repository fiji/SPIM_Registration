package spim.fiji.plugin.interestpointregistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
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
	public boolean register( final boolean isTimeSeriesRegistration )
	{
		for ( final TimePoint t : timepointsToProcess )
		{
			final HashMap< ViewId, List< InterestPoint > > pointLists = this.getInterestPoints( t );
			
		}
		
		return true;
	}

	@Override
	public GeometricHashing3d newInstance( final SpimData2 spimData, final ArrayList< TimePoint > timepointsToProcess, final ArrayList< ChannelProcess > channelsToProcess )
	{
		return new GeometricHashing3d( spimData, timepointsToProcess, channelsToProcess );
	}

	@Override
	public String getDescription() { return "Fast 3d geometric hashing";}

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
