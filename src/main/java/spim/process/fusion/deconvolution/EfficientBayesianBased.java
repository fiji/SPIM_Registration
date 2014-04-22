package spim.process.fusion.deconvolution;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.export.ImgExport;

public class EfficientBayesianBased extends Fusion
{
	public EfficientBayesianBased(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
		
		// we want the arrayimg by default
		BoundingBox.defaultImgType = 0;
	}

	@Override
	public boolean fuseData(BoundingBox bb, ImgExport exporter) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean queryParameters() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void queryAdditionalParameters(GenericDialog gd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean parseAdditionalParameters(GenericDialog gd) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Fusion newInstance(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess)
	{
		return new EfficientBayesianBased( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Multi-view deconvolution"; }
	
	@Override
	public boolean supports16BitUnsigned() { return false; }

	@Override
	public boolean supportsDownsampling() { return false; }

	@Override
	public boolean compressBoundingBoxDialog() { return false; }
}
