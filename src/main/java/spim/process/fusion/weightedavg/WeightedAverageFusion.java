package spim.process.fusion.weightedavg;

import ij.gui.GenericDialog;

import java.awt.Choice;
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
import spim.process.fusion.FusionHelper;
import spim.process.fusion.export.ImgExport;

public class WeightedAverageFusion extends Fusion
{
	public enum WeightedAvgFusionType { FUSEDATA, INDEPENDENT };
	final WeightedAvgFusionType type;
	
	public static int defaultNumParalellViewsIndex = 0;
	protected int numParalellViews = 1;
	
	protected Choice sequentialViews = null;

	public WeightedAverageFusion(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess, 
			final WeightedAvgFusionType type )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
		
		this.type = type;		
	}
	
	public WeightedAvgFusionType getFusionType() { return type; }
	
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
		final ProcessFusion process;
		
		if ( getFusionType() == WeightedAvgFusionType.FUSEDATA && numParalellViews == 0 )
			process = new ProcessParalell( spimData, anglesToProcess, illumsToProcess, bb, useBlending, useContentBased );
		else if ( getFusionType() == WeightedAvgFusionType.FUSEDATA )
			process = new ProcessSequential( spimData, anglesToProcess, illumsToProcess, bb, useBlending, useContentBased, numParalellViews );
		else
			process = new ProcessIndependent( spimData, anglesToProcess, illumsToProcess, bb, exporter );
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
			{
				final String title = "TP" + t.getName() + "_Ch" + c.getName(); 
				if ( bb.getPixelType() == 0 )
				{
					exporter.exportImage(
							process.fuseStack( new FloatType(), getInterpolatorFactory( new FloatType() ), t , c ),
							bb,
							title );
				}
				else
				{
					exporter.exportImage(
							process.fuseStack( new UnsignedShortType(), getInterpolatorFactory( new UnsignedShortType() ), t , c ),
							bb,
							title );
				}
			}

		return true;
	}

	@Override
	public boolean queryParameters()
	{
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
		return new WeightedAverageFusion( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess, type );
	}

	@Override
	public String getDescription()
	{
		if ( type == WeightedAvgFusionType.FUSEDATA )
			return "Weighted-average fusion";
		else
			return "No fusion, create individual registered images";
	}

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
		
		if ( this.getFusionType() == WeightedAvgFusionType.FUSEDATA )
		{
			int maxViews = 0;
			
			for ( final TimePoint t : timepointsToProcess )
				for ( final Channel c : channelsToProcess )
					maxViews = Math.max( maxViews, FusionHelper.assembleInputData( spimData, t, c, anglesToProcess, illumsToProcess).size() );
			
			// any choice but all views
			final String[] views = new String[ maxViews ];
			
			views[ 0 ] = "All";
			
			for ( int i = 1; i < views.length; ++i )
				views[ i ] = "" + i;
			
			if ( defaultNumParalellViewsIndex < 0 && defaultNumParalellViewsIndex >= views.length )
				defaultNumParalellViewsIndex = 0;
			
			gd.addChoice( "Process_views_in_paralell", views, views[ defaultNumParalellViewsIndex ] );
			this.sequentialViews = (Choice)gd.getChoices().lastElement();
		}
		
		if ( this.getFusionType() == WeightedAvgFusionType.FUSEDATA )
		{
			gd.addCheckbox( "Blend images smoothly", Fusion.defaultUseBlending );
			gd.addCheckbox( "Content-based fusion", Fusion.defaultUseContentBased );
		}
		gd.addChoice( "Interpolation", Fusion.interpolationTypes, Fusion.interpolationTypes[ Fusion.defaultInterpolation ] );
	}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd )
	{
		if ( this.getFusionType() == WeightedAvgFusionType.FUSEDATA )
		{
			defaultNumParalellViewsIndex = gd.getNextChoiceIndex();
			this.numParalellViews = defaultNumParalellViewsIndex;
			this.useBlending = Fusion.defaultUseBlending = gd.getNextBoolean();
			this.useContentBased = Fusion.defaultUseContentBased = gd.getNextBoolean();
		}
		else
		{
			this.useBlending = this.useContentBased = false;
		}
		this.interpolation = Fusion.defaultInterpolation = gd.getNextChoiceIndex();

		return true;
	}
	
	@Override
	public long totalRAM( final long fusedSizeMB, final int bytePerPixel )
	{
		if ( type == WeightedAvgFusionType.FUSEDATA && sequentialViews.getSelectedIndex() == 0 )
			return fusedSizeMB + (getMaxNumViewsPerTimepoint() * (avgPixels/ ( 1024*1024 )) * bytePerPixel);
		else if ( type == WeightedAvgFusionType.FUSEDATA )
			return fusedSizeMB + ((sequentialViews.getSelectedIndex()) * (avgPixels/ ( 1024*1024 )) * bytePerPixel);
		else
			return fusedSizeMB + (avgPixels/ ( 1024*1024 )) * bytePerPixel;
	
	}
}
