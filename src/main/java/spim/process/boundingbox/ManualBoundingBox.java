package spim.process.boundingbox;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.GUIHelper;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.spimdata.SpimData2;

public class ManualBoundingBox extends BoundingBox
{	
	public ManualBoundingBox(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess)
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public boolean queryParameters()
	{
		final GenericDialog gd = new GenericDialog( "Manually define Bounding Box" );

		gd.addMessage( "Note: these coordinates are in global coordinates as shown\n" +
				"in the main status bar of Fiji in a fused datasets", GUIHelper.smallStatusFont );
		gd.addMessage( "", GUIHelper.smallStatusFont );
		
		gd.addNumericField( "Minimal_X", BoundingBox.minStatic[ 0 ], 0 );
		gd.addNumericField( "Minimal_Y", BoundingBox.minStatic[ 1 ], 0 );
		gd.addNumericField( "Minimal_Z", BoundingBox.minStatic[ 2 ], 0 );

		gd.addMessage( "" );
		
		gd.addNumericField( "Maximal_X", BoundingBox.maxStatic[ 0 ], 0 );
		gd.addNumericField( "Maximal_Y", BoundingBox.maxStatic[ 1 ], 0 );
		gd.addNumericField( "Maximal_Z", BoundingBox.maxStatic[ 2 ], 0 );
		
		gd.addMessage( "" );
		
		gd.addSlider( "Downsample fused dataset", 1.0, 10.0, BoundingBox.staticDownsampling );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.min[ 0 ] = (int)Math.round( gd.getNextNumber() );
		this.min[ 1 ] = (int)Math.round( gd.getNextNumber() );
		this.min[ 2 ] = (int)Math.round( gd.getNextNumber() );

		this.max[ 0 ] = (int)Math.round( gd.getNextNumber() );
		this.max[ 1 ] = (int)Math.round( gd.getNextNumber() );
		this.max[ 2 ] = (int)Math.round( gd.getNextNumber() );
		
		this.downsampling = BoundingBox.staticDownsampling = (int)Math.round( gd.getNextNumber() );

		if ( min[ 0 ] > max[ 0 ] || min[ 1 ] > max[ 1 ] || min[ 2 ] > max[ 2 ] )
		{
			IOFunctions.println( "Invalid coordinates, min cannot be larger than max" );
			return false;
		}
		
		BoundingBox.minStatic[ 0 ] = min[ 0 ];
		BoundingBox.minStatic[ 1 ] = min[ 1 ];
		BoundingBox.minStatic[ 2 ] = min[ 2 ];
		BoundingBox.maxStatic[ 0 ] = max[ 0 ];
		BoundingBox.maxStatic[ 1 ] = max[ 1 ];
		BoundingBox.maxStatic[ 2 ] = max[ 2 ];
		
		return true;
	}


	@Override
	public ManualBoundingBox newInstance(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess )
	{
		return new ManualBoundingBox( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Manually define Bounding Box"; }
}
