package spim.process.fusion.weightedavg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.FinalDimensions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.fusion.boundingbox.BoundingBoxGUI;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.export.FixedNameImgTitler;
import spim.process.fusion.export.ImgExport;
import spim.process.fusion.export.ImgExportTitle;

public class WeightedAverageFusion extends Fusion
{
	public WeightedAverageFusion(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public boolean fuseData( final BoundingBoxGUI bb, final ImgExport exporter )
	{
		// set up naming scheme
		final FixedNameImgTitler titler = new FixedNameImgTitler( "" );
		if ( exporter instanceof ImgExportTitle )
			( (ImgExportTitle)exporter).setImgTitler( titler );

		final ProcessFusion process = new ProcessVirtual( spimData, viewIdsToProcess, bb, bb.getDownSampling(), getInterpolation(), useBlending, useContentBased );

		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
			{
				final List< Angle > anglesToProcess = SpimData2.getAllAnglesForChannelTimepointSorted( spimData, viewIdsToProcess, c, t );
				final List< Illumination > illumsToProcess = SpimData2.getAllIlluminationsForChannelTimepointSorted( spimData, viewIdsToProcess, c, t );

				titler.setTitle( "TP" + t.getName() + "_Ch" + c.getName() + FusionHelper.getIllumName( illumsToProcess ) + FusionHelper.getAngleName( anglesToProcess ) );
				if ( bb.getPixelType() == 0 )
				{
					exporter.exportImage(
							process.fuseStack( new FloatType() ,t , c, bb.getImgFactory( new FloatType() ) ),
							bb,
							t,
							newViewsetups.get( SpimData2.getViewSetup( spimData.getSequenceDescription().getViewSetupsOrdered(), c, anglesToProcess.get( 0 ), illumsToProcess.get( 0 ) ) ));
				}
				else
				{
					exporter.exportImage(
							process.fuseStack( new UnsignedShortType() ,t , c, bb.getImgFactory( new UnsignedShortType() ) ),
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
	public WeightedAverageFusion newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new WeightedAverageFusion( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription() { return "Weighted-average fusion"; }

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

		gd.addCheckbox( "Blend images smoothly", Fusion.defaultUseBlending );
		gd.addCheckbox( "Content-based fusion", Fusion.defaultUseContentBased );
		gd.addChoice( "Interpolation", Fusion.interpolationTypes, Fusion.interpolationTypes[ Fusion.defaultInterpolation ] );
	}

	@Override
	public boolean parseAdditionalParameters( final GenericDialog gd )
	{
		this.useBlending = Fusion.defaultUseBlending = gd.getNextBoolean();
		this.useContentBased = Fusion.defaultUseContentBased = gd.getNextBoolean();
		this.interpolation = Fusion.defaultInterpolation = gd.getNextChoiceIndex();

		return true;
	}
	
	@Override
	public long totalRAM( final long fusedSizeMB, final int bytePerPixel )
	{
		return fusedSizeMB;
	
	}

	@Override
	protected Map< ViewSetup, ViewSetup > createNewViewSetups( final BoundingBoxGUI bb )
	{
		return assembleNewViewSetupsFusion(
				spimData,
				viewIdsToProcess,
				bb,
				"Fused",
				"Fused" );
	}

	/**
	 * Creates one new Angle and one new Illumination for the fused dataset.
	 * The size of the List&gt; ViewSetup &lt; is therefore equal to the number of channels
	 * 
	 * @param spimData
	 * @param viewIdsToProcess
	 * @param bb
	 * @param newAngleName
	 * @param newIlluminationName
	 * @return
	 */
	public static Map< ViewSetup, ViewSetup > assembleNewViewSetupsFusion(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final BoundingBoxGUI bb,
			final String newAngleName,
			final String newIlluminationName )
	{
		final HashMap< ViewSetup, ViewSetup > map = new HashMap< ViewSetup, ViewSetup >();

		int maxViewSetupIndex = -1;
		int maxAngleIndex = -1;
		int maxIllumIndex = -1;

		// make a new viewsetup
		for ( final ViewSetup v : spimData.getSequenceDescription().getViewSetups().values() )
			maxViewSetupIndex = Math.max( maxViewSetupIndex, v.getId() );

		// that has a new angle
		for ( final Angle a : spimData.getSequenceDescription().getAllAngles().values() )
			maxAngleIndex = Math.max( maxAngleIndex, a.getId() );

		// and a new illumination
		for ( final Illumination i : spimData.getSequenceDescription().getAllIlluminations().values() )
			maxIllumIndex = Math.max( maxIllumIndex, i.getId() );

		final Angle newAngle = new Angle( maxAngleIndex + 1, newAngleName + "_" + ( maxAngleIndex + 1 ) );
		final Illumination newIllum = new Illumination( maxIllumIndex + 1, newIlluminationName + "_" + ( maxIllumIndex + 1 ) );
		
		final String unit = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getVoxelSize().unit();
		
		// get the minimal resolution of all calibrations relative to the downsampling
		final double minResolution = Apply_Transformation.assembleAllMetaData(
				spimData.getSequenceDescription(),
				spimData.getSequenceDescription().getViewDescriptions().values() ) * bb.getDownSampling();

		// one new new viewsetup for every channel that is fused (where fused means combining one or more angles&illuminations into one new image)
		for ( final Channel channel : SpimData2.getAllChannelsSorted( spimData, viewIdsToProcess ) )
		{
			final ViewSetup newSetup = new ViewSetup(
					++maxViewSetupIndex,
					null,
					new FinalDimensions( bb.getDimensions() ),
					new FinalVoxelDimensions ( unit, new double[]{ minResolution, minResolution, minResolution } ),
					channel,
					newAngle,
					newIllum );
			
			// all viewsetups of the current channel that are fused point to the same new viewsetup
			for ( final ViewId viewId : viewIdsToProcess )
			{
				final ViewDescription oldVD = spimData.getSequenceDescription().getViewDescription( viewId );
				final ViewSetup oldSetup = oldVD.getViewSetup();

				// is this old setup from the current channel? then point to it
				if ( oldVD.isPresent() && oldSetup.getChannel().getId() == channel.getId() )
					map.put( oldSetup, newSetup );
			}
		}

		return map;
	}

	/**
	 * Duplicates all Angles and Illuminations that are processed.
	 * The size of the List&gt; ViewSetup &lt; is therefore equal to
	 * the number of channels * number of processed angles * number of processed illuminations
	 * 
	 * @param spimData
	 * @param viewIdsToProcess
	 * @param bb
	 * @return
	 */
	public static Map< ViewSetup, ViewSetup > assembleNewViewSetupsSequential(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess,
			final BoundingBoxGUI bb )
	{
		final HashMap< ViewSetup, ViewSetup > map = new HashMap< ViewSetup, ViewSetup >();

		// make many new view setups with new angles&illuminations
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
				spimData.getSequenceDescription().getViewDescriptions().values() ) * bb.getDownSampling();

		// every combination of old angle and old illumination of each channel gets a new viewsetup
		final List< Angle > oldAngles = SpimData2.getAllAnglesSorted( spimData, viewIdsToProcess );
		final List< Illumination > oldIllums = SpimData2.getAllIlluminationsSorted( spimData, viewIdsToProcess );

		final HashMap< Angle, Angle > mapOldToNewAngles = new HashMap< Angle, Angle >();
		final HashMap< Illumination, Illumination > mapOldToNewIlluminations = new HashMap< Illumination, Illumination >();

		//final List< Pair< Angle, Angle > > mapOldToNewAngles = new ArrayList< Pai r< Angle, Angle > >();
		//final List< Pair< Illumination, Illumination > > mapOldToNewIlluminations = new ArrayList< Pair< Illumination, Illumination > >();

		for ( int i = 0; i < oldAngles.size(); ++i )
			mapOldToNewAngles.put(
				oldAngles.get( i ),
				new Angle(
					maxAngleIndex + i + 1,
					"Transf_" + oldAngles.get( i ).getName() + "_" + maxAngleIndex + i + 1,
					oldAngles.get( i ).getRotationAngleDegrees(),
					oldAngles.get( i ).getRotationAxis() ) );

		for ( int i = 0; i < oldIllums.size(); ++i )
			mapOldToNewIlluminations.put(
				oldIllums.get( i ),
				new Illumination(
					maxIllumIndex + i + 1,
					"Transf_" + oldIllums.get( i ).getName() + "_" + maxIllumIndex + i + 1 ) );

		// now every viewsetup of every viewdescription that is fused (maybe for several timepoints) gets a new viewsetup
		for ( final ViewId viewId : viewIdsToProcess )
		{
			final ViewDescription oldVD = spimData.getSequenceDescription().getViewDescription( viewId );

			if ( oldVD.isPresent() )
			{
				final ViewSetup oldSetup = oldVD.getViewSetup();

				// no new viewsetup defined for this old viewsetup
				if ( !map.containsKey( oldSetup ) )
				{
					// get the new angle & illumination object
					final Channel channel = oldSetup.getChannel();
					final Angle oldAngle = oldSetup.getAngle();
					final Illumination oldIllumination = oldSetup.getIllumination();

					// make the new viewsetup
					final Angle newAngle = mapOldToNewAngles.get( oldAngle );
					final Illumination newIllumination = mapOldToNewIlluminations.get( oldIllumination );

					final ViewSetup newSetup = new ViewSetup( 
							++maxViewSetupIndex,
							null,
							new FinalDimensions( bb.getDimensions() ), 
							new FinalVoxelDimensions ( unit, new double[]{ minResolution, minResolution, minResolution } ),
							channel,
							newAngle,
							newIllumination );

					map.put( oldSetup, newSetup );
				}
			}
		}

		return map;
	}
}
