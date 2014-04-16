package spim.process.fusion.weightedavg;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import net.imglib2.RandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.export.ImgExport;

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
		
	public < T extends RealType< T > > InterpolatorFactory< T, RandomAccessible< T > > getInterpolatorFactory( final T type )
	{
		if ( getInterpolation() == 0 )
			return new NearestNeighborInterpolatorFactory<T>();
		else
			return new NLinearInterpolatorFactory< T >();
	}
	
	@Override
	public boolean fuseData( final BoundingBox bb, final ImgExport exporter ) 
	{
		//TODO: ask for which one
		final ProcessFusion process = new ProcessParalell( spimData, anglesToProcess, illumsToProcess, bb );

		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
			{
				final String title = "TP: " + t.getName() + ", Ch: " + c.getName(); 
				if ( bb.getPixelType() == 0 )
				{
					exporter.exportImage(
							process.fuseStack( new FloatType(), getInterpolatorFactory( new FloatType() ), t , c ),
							bb,
							this,
							title );
				}
				else
				{
					exporter.exportImage(
							process.fuseStack( new UnsignedShortType(), getInterpolatorFactory( new UnsignedShortType() ), t , c ),
							bb,
							this,
							title );
				}
			}

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

	@Override
	public boolean supports16BitUnsigned() { return true; }

	@Override
	public boolean supportsDownsampling() { return true; }

	@Override
	public boolean compressBoundingBoxDialog() { return false; }

	@Override
	public void queryAdditionalParameters( final GenericDialog gd )
	{
		if ( Fusion.defaultInterpolation >= Fusion.interpolationTypes.length )
			Fusion.defaultInterpolation = Fusion.interpolationTypes.length - 1;
		
		gd.addChoice( "Interpolation", Fusion.interpolationTypes, Fusion.interpolationTypes[ Fusion.defaultInterpolation ] );
	}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd )
	{
		this.interpolation = Fusion.defaultInterpolation = gd.getNextChoiceIndex();
		return true;
	}
}
