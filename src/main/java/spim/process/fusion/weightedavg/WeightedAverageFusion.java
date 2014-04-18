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
import spim.process.fusion.FusionHelper;
import spim.process.fusion.export.ImgExport;

public class WeightedAverageFusion extends Fusion
{
	public enum WeightedAvgFusionType { PARALELL, SEQUENTIAL, INDEPENDENT };
	final WeightedAvgFusionType type;
	
	public static int defaultNumParalellViewsIndex = 0;
	protected int numParalellViews = 1;

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
		
		if ( getFusionType() == WeightedAvgFusionType.PARALELL )
			process = new ProcessParalell( spimData, anglesToProcess, illumsToProcess, bb, useBlending, useContentBased );
		else if ( getFusionType() == WeightedAvgFusionType.SEQUENTIAL )
			process = new ProcessSequential( spimData, anglesToProcess, illumsToProcess, bb, useBlending, useContentBased, numParalellViews );
		else
			process = new ProcessIndependent( spimData, anglesToProcess, illumsToProcess, bb, exporter );
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
			{
				final String title = "TP: " + t.getName() + ", Ch: " + c.getName(); 
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
		if ( type == WeightedAvgFusionType.PARALELL )
			return "Weighted-average based image fusion (process all views in paralell)";
		else if ( type == WeightedAvgFusionType.SEQUENTIAL )
			return "Weighted-average based image fusion (process views sequentially)";
		else
			return "Create individually registered views (no fusion)";
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
		
		if ( this.getFusionType() == WeightedAvgFusionType.SEQUENTIAL )
		{
			int maxViews = 0;
			
			for ( final TimePoint t : timepointsToProcess )
				for ( final Channel c : channelsToProcess )
					maxViews = Math.max( maxViews, FusionHelper.assembleInputData( spimData, t, c, anglesToProcess, illumsToProcess).size() );
			
			// any choice but all views
			final String[] views = new String[ maxViews - 1 ];
			
			for ( int i = 0; i < views.length; ++i )
				views[ i ] = "" + ( i + 1 );
			
			if ( defaultNumParalellViewsIndex < 0 && defaultNumParalellViewsIndex >= views.length )
				defaultNumParalellViewsIndex = 0;
			
			gd.addChoice( "Process_views_in_paralell", views, views[ defaultNumParalellViewsIndex ] );
		}
		
		if ( this.getFusionType() == WeightedAvgFusionType.PARALELL || this.getFusionType() == WeightedAvgFusionType.SEQUENTIAL )
		{
			gd.addCheckbox( "Blend images smoothly", Fusion.defaultUseBlending );
			gd.addCheckbox( "Content-based fusion", Fusion.defaultUseContentBased );
		}
		gd.addChoice( "Interpolation", Fusion.interpolationTypes, Fusion.interpolationTypes[ Fusion.defaultInterpolation ] );
	}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd )
	{
		if ( this.getFusionType() == WeightedAvgFusionType.SEQUENTIAL )
		{
			defaultNumParalellViewsIndex = gd.getNextChoiceIndex();
			this.numParalellViews = defaultNumParalellViewsIndex + 1;
		}
		
		if ( this.getFusionType() == WeightedAvgFusionType.PARALELL || this.getFusionType() == WeightedAvgFusionType.SEQUENTIAL )
		{
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
}
