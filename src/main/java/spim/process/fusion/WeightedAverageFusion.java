package spim.process.fusion;

import ij.ImagePlus;

import java.util.ArrayList;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.type.numeric.real.FloatType;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;

public class WeightedAverageFusion extends Fusion
{
	public WeightedAverageFusion(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess)
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public boolean fuseData( final BoundingBox bb ) 
	{
		final WeightedAvgFusionParalell<FloatType> fusion = new WeightedAvgFusionParalell<FloatType>( bb, new FloatType(), new ArrayImgFactory<FloatType>(), spimData );

		final Img< FloatType > img = fusion.fuseData(
				new NLinearInterpolatorFactory< FloatType >(),
				timepointsToProcess.get( 0 ), 
				channelsToProcess.get( 0 ), 
				anglesToProcess, 
				illumsToProcess );
		
		ImageJFunctions.show( img );
		/*
		ImagePlus imp = ImageJFunctions.wrapFloat( img, "fused" );
		
		imp.getCalibration().xOrigin = bb.min( 0 );
		imp.getCalibration().yOrigin = bb.min( 1 );
		imp.getCalibration().zOrigin = bb.min( 2 );
		
		imp.updateAndDraw();
		imp.show();
		*/
		return true;
	}

	@Override
	public boolean queryParameters()
	{
		// TODO Auto-generated method stub
		return true;
	}


	@Override
	public WeightedAverageFusion newInstance(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess )
	{
		return new WeightedAverageFusion( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Weighted-average based Image Fusion"; }
}
