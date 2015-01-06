package spim.process.fusion.weightedavg;

import ij.gui.GenericDialog;

import java.awt.Choice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.export.FixedNameImgTitler;
import spim.process.fusion.export.ImgExport;
import spim.process.fusion.export.ImgExportTitle;

public class WeightedAverageFusion extends Fusion
{
	public enum WeightedAvgFusionType { FUSEDATA, INDEPENDENT };
	final WeightedAvgFusionType type;
	
	public static int defaultNumParalellViewsIndex = 0;
	protected int numParalellViews = 1;
	
	protected Choice sequentialViews = null;

	public WeightedAverageFusion(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess, 
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
		// set up naming scheme
		final FixedNameImgTitler titler = new FixedNameImgTitler( "" );
		if ( exporter instanceof ImgExportTitle )
			( (ImgExportTitle)exporter).setImgTitler( titler );

		final ProcessFusion process;
		
		if ( getFusionType() == WeightedAvgFusionType.FUSEDATA && numParalellViews == 0 )
			process = new ProcessParalell( spimData, anglesToProcess, illumsToProcess, bb, useBlending, useContentBased );
		else if ( getFusionType() == WeightedAvgFusionType.FUSEDATA )
			process = new ProcessSequential( spimData, anglesToProcess, illumsToProcess, bb, useBlending, useContentBased, numParalellViews );
		else
			process = new ProcessIndependent( spimData, anglesToProcess, illumsToProcess, bb, exporter, newViewsetups );

		String illumName = "_Ill" + illumsToProcess.get( 0 ).getName();

		for ( int i = 1; i < illumsToProcess.size(); ++i )
			illumName += "," + illumsToProcess.get( i ).getName();

		String angleName = "_Ang" + anglesToProcess.get( 0 ).getName();

		for ( int i = 1; i < anglesToProcess.size(); ++i )
			angleName += "," + anglesToProcess.get( i ).getName();

		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
			{
				titler.setTitle( "TP" + t.getName() + "_Ch" + c.getName() + illumName + angleName );
				if ( bb.getPixelType() == 0 )
				{
					exporter.exportImage(
							process.fuseStack( new FloatType(), getInterpolatorFactory( new FloatType() ), t , c ),
							bb,
							t,
							newViewsetups.get( SpimData2.getViewSetup( spimData.getSequenceDescription().getViewSetupsOrdered(), c, anglesToProcess.get( 0 ), illumsToProcess.get( 0 ) ) ));
				}
				else
				{
					exporter.exportImage(
							process.fuseStack( new UnsignedShortType(), getInterpolatorFactory( new UnsignedShortType() ), t , c ),
							bb,
							t,
							newViewsetups.get( SpimData2.getViewSetup( spimData.getSequenceDescription().getViewSetupsOrdered(), c, anglesToProcess.get( 0 ), illumsToProcess.get( 0 ) ) ));
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
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess )
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

	@Override
	protected Map< ViewSetup, ViewSetup > createNewViewSetups( final BoundingBox bb )
	{
		if ( type == WeightedAvgFusionType.FUSEDATA )
		{
			return assembleNewViewSetupsFusion(
					spimData,
					channelsToProcess,
					illumsToProcess,
					anglesToProcess,
					bb,
					"Fused",
					"Fused" );
		}
		else
		{
			return assembleNewViewSetupsSequential(
					spimData,
					channelsToProcess,
					illumsToProcess,
					anglesToProcess,
					bb );
		}
	}

	/**
	 * Creates one new Angle and one new Illumination for the fused dataset.
	 * The size of the List< ViewSetup > is therefore equal to the number of channels
	 * 
	 * @param spimData
	 * @param channelsToProcess
	 * @param bb
	 * @param newViewSetupName
	 * @param newAngleName
	 * @param newIlluminationName
	 * @return
	 */
	public static Map< ViewSetup, ViewSetup > assembleNewViewSetupsFusion(
			final SpimData2 spimData,
			final List< Channel > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< Angle > anglesToProcess,
			final BoundingBox bb,
			final String newAngleName,
			final String newIlluminationName )
	{
		final HashMap< ViewSetup, ViewSetup > map = new HashMap< ViewSetup, ViewSetup >();

		int maxViewSetupIndex = -1;
		int maxAngleIndex = -1;
		int maxIllumIndex = -1;
		
		for ( final ViewSetup v : spimData.getSequenceDescription().getViewSetups().values() )
			maxViewSetupIndex = Math.max( maxViewSetupIndex, v.getId() );

		for ( final Angle a : spimData.getSequenceDescription().getAllAngles().values() )
			maxAngleIndex = Math.max( maxAngleIndex, a.getId() );

		for ( final Illumination i : spimData.getSequenceDescription().getAllIlluminations().values() )
			maxIllumIndex = Math.max( maxIllumIndex, i.getId() );

		final Angle newAngle = new Angle( maxAngleIndex + 1, newAngleName + "_" + ( maxAngleIndex + 1 ) );
		final Illumination newIllum = new Illumination( maxIllumIndex + 1, newIlluminationName + "_" + ( maxIllumIndex + 1 ) );
		
		final String unit = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().unit();
		
		// get the minimal resolution of all calibrations relative to the downsampling
		final double minResolution = Apply_Transformation.assembleAllMetaData(
				spimData.getSequenceDescription(),
				spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered(),
				spimData.getSequenceDescription().getAllChannelsOrdered(),
				spimData.getSequenceDescription().getAllIlluminationsOrdered(),
				spimData.getSequenceDescription().getAllAnglesOrdered() ) * bb.getDownSampling();

		for ( final Channel channel : channelsToProcess )
		{
			final ViewSetup newSetup = new ViewSetup( 
					++maxViewSetupIndex,
					null,
					new FinalDimensions( bb.getDimensions() ), 
					new FinalVoxelDimensions ( unit, new double[]{ minResolution, minResolution, minResolution } ),
					channel,
					newAngle,
					newIllum );
			
			// all viewsetups of the processed illuminations and angles map to the same new viewsetup
			for ( final Illumination i : illumsToProcess )
				for ( final Angle a : anglesToProcess )
				{
					map.put(
						SpimData2.getViewSetup( spimData.getSequenceDescription().getViewSetupsOrdered(), channel, a, i ),
						newSetup );
				}
		}

		return map;
	}

	/**
	 * Duplicates all Angles and Illuminations that are processed.
	 * The size of the List< ViewSetup > is therefore equal to 
	 * the number of channels * number of processed angles * number of processed illuminations
	 * 
	 * @param spimData
	 * @param channelsToProcess
	 * @param illumsToProcess
	 * @param anglesToProcess
	 * @param bb
	 * @return
	 */
	public static Map< ViewSetup, ViewSetup > assembleNewViewSetupsSequential(
			final SpimData2 spimData,
			final List< Channel > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< Angle > anglesToProcess,
			final BoundingBox bb )
	{
		final HashMap< ViewSetup, ViewSetup > map = new HashMap< ViewSetup, ViewSetup >();

		int maxViewSetupIndex = -1;
		int maxAngleIndex = -1;
		int maxIllumIndex = -1;
		
		for ( final ViewSetup v : spimData.getSequenceDescription().getViewSetups().values() )
			maxViewSetupIndex = Math.max( maxViewSetupIndex, v.getId() );

		for ( final Angle a : spimData.getSequenceDescription().getAllAngles().values() )
			maxAngleIndex = Math.max( maxAngleIndex, a.getId() );

		for ( final Illumination i : spimData.getSequenceDescription().getAllIlluminations().values() )
			maxIllumIndex = Math.max( maxIllumIndex, i.getId() );

		final String unit = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().unit();
		
		// get the minimal resolution of all calibrations relative to the downsampling
		final double minResolution = Apply_Transformation.assembleAllMetaData(
				spimData.getSequenceDescription(),
				spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered(),
				spimData.getSequenceDescription().getAllChannelsOrdered(),
				spimData.getSequenceDescription().getAllIlluminationsOrdered(),
				spimData.getSequenceDescription().getAllAnglesOrdered() ) * bb.getDownSampling();

		final List< Angle > newAngles = new ArrayList< Angle >();
		final List< Illumination > newIllums = new ArrayList< Illumination >();

		for ( int i = 0; i < anglesToProcess.size(); ++i )
			newAngles.add( new Angle(
					maxAngleIndex + i + 1,
					"Transf_" + anglesToProcess.get( i ).getName() + "_" + maxAngleIndex + i + 1,
					anglesToProcess.get( i ).getRotationAngleDegrees(),
					anglesToProcess.get( i ).getRotationAxis() ) );

		for ( int i = 0; i < illumsToProcess.size(); ++i )
			newIllums.add( new Illumination(
					maxIllumIndex + i + 1,
					"Transf_" + illumsToProcess.get( i ).getName() + "_" + maxIllumIndex + i + 1 ) );

		for ( final Channel channel : channelsToProcess )
			for ( int i = 0; i < illumsToProcess.size(); ++i )
				for ( int a = 0; a < anglesToProcess.size(); ++a )
				{
					final ViewSetup oldSetup = SpimData2.getViewSetup( spimData.getSequenceDescription().getViewSetupsOrdered(), channel, anglesToProcess.get( a ), illumsToProcess.get( i ) );
					final ViewSetup newSetup = new ViewSetup( 
							++maxViewSetupIndex,
							null,
							new FinalDimensions( bb.getDimensions() ), 
							new FinalVoxelDimensions ( unit, new double[]{ minResolution, minResolution, minResolution } ),
							channel,
							newAngles.get( a ),
							newIllums.get( i ) );

					map.put( oldSetup, newSetup );
				}

		return map;
	}
}
