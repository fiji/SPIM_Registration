package spim.process.deconvolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import bdv.util.ConstantRandomAccessible;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.spimdata.SpimData2;
import spim.process.deconvolution.normalization.NormalizingRandomAccessibleInterval;
import spim.process.fusion.FusionTools;
import spim.process.fusion.FusionTools.ImgDataType;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.TransformView;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.fusion.transformed.TransformWeight;
import spim.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval;
import spim.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval.CombineType;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class ProcessInputImages< V extends ViewId >
{
	public static int defaultBlendingRangeNumber = 12;
	public static int defaultBlendingBorderNumber = -8;
	public static int cellDim = 32;
	public static int maxCacheSize = 10000;

	// for additional smoothing of weights in areas where many views contribute less than 100%
	public static float maxDiffRange = 0.1f;
	public static float scalingRange = 0.05f;
	public static boolean additionalSmoothBlending = false;

	final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? extends BasicImgLoader > > spimData;
	final ArrayList< Group< V > > groups;
	final Interval bb;
	Interval downsampledBB;
	final double downsampling;
	final boolean useWeightsFusion, useWeightsDecon;
	ImgFactory< FloatType > factory = new CellImgFactory<>( 64 );

	final HashMap< Group< V >, RandomAccessibleInterval< FloatType > > images;
	final HashMap< Group< V >, RandomAccessibleInterval< FloatType > > unnormalizedWeights, normalizedWeights;
	final HashMap< V, AffineTransform3D > models;

	public ProcessInputImages(
			final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? extends BasicImgLoader > > spimData,
			final Collection< Group< V > > groups,
			final Interval bb,
			final double downsampling,
			final boolean useWeightsFusion,
			final boolean useWeightsDecon )
	{
		this.spimData = spimData;
		this.groups = SpimData2.filterGroupsForMissingViews( spimData, groups );
		this.bb = bb;
		this.downsampling = downsampling;
		this.useWeightsDecon = useWeightsDecon;
		this.useWeightsFusion = useWeightsFusion;

		this.images = new HashMap<>();
		this.unnormalizedWeights = new HashMap<>();
		this.normalizedWeights = new HashMap<>();
		this.models = new HashMap<>();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Remaining groups: " + this.groups.size() );
	}

	public ProcessInputImages(
			final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? extends BasicImgLoader > > spimData,
			final Collection< Group< V > > groups,
			final Interval bb )
	{
		this( spimData, groups, bb, Double.NaN, true, true );
	}

	public Collection< Group< V > > getGroups() { return groups; }
	public Interval getBoundingBox() { return bb; }
	public Interval getDownsampledBoundingBox() { return downsampledBB; }
	public HashMap< V, AffineTransform3D > getDownsampledModels() { return models; }
	public HashMap< Group< V >, RandomAccessibleInterval< FloatType > > getImages() { return images; }
	public HashMap< Group< V >, RandomAccessibleInterval< FloatType > > getNormalizedWeights() { return normalizedWeights; }
	public HashMap< Group< V >, RandomAccessibleInterval< FloatType > > getUnnormalizedWeights() { return unnormalizedWeights; }

	public void fuseGroups()
	{
		this.downsampledBB = fuseGroups(
				spimData,
				images,
				unnormalizedWeights,
				models,
				groups,
				bb,
				downsampling,
				useWeightsFusion ? FusionTools.defaultBlendingRange.clone() : null,
				useWeightsFusion ? FusionTools.defaultBlendingBorder.clone() : null,
				useWeightsDecon ? new float[]{ defaultBlendingRangeNumber, defaultBlendingRangeNumber, defaultBlendingRangeNumber } : null,
				useWeightsDecon ? new float[]{ defaultBlendingBorderNumber, defaultBlendingBorderNumber, defaultBlendingBorderNumber } : null );
	}

	public void deVirtualizeImages( final ImgDataType type ) { deVirtualize( type, groups, factory, cellDim, maxCacheSize, images ); }
	public void deVirtualizeUnnormalizedWeights( final ImgDataType type ) { deVirtualize( type, groups, factory, cellDim, maxCacheSize, unnormalizedWeights ); }
	public void deVirtualizeNormalizedWeights( final ImgDataType type ) { deVirtualize( type, groups, factory, cellDim, maxCacheSize, normalizedWeights ); }

	public void normalizeWeights() { normalizeWeights( 1.0 ); }
	public void normalizeWeights( final double osemspeedup ) { normalizeWeights( osemspeedup, additionalSmoothBlending, maxDiffRange, scalingRange ); }
	public void normalizeWeights(
			final double osemspeedup,
			final boolean additionalSmoothBlending,
			final float maxDiffRange,
			final float scalingRange )
	{
		normalizedWeights.putAll(
			normalizeWeights(
				unnormalizedWeights,
				groups,
				osemspeedup,
				additionalSmoothBlending,
				maxDiffRange,
				scalingRange ) );
	}

	public static < V extends ViewId > HashMap< Group< V >, RandomAccessibleInterval< FloatType > > normalizeWeights(
			final HashMap< Group< V >, RandomAccessibleInterval< FloatType > > unnormalizedWeights,
			final List< Group< V > > groups,
			final double osemspeedup,
			final boolean additionalSmoothBlending,
			final float maxDiffRange,
			final float scalingRange )
	{
		final HashMap< Group< V >, RandomAccessibleInterval< FloatType > > normalizedWeights = new HashMap<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > originalWeights = new ArrayList<>();

		// ordered as in the group list
		for ( int i = 0; i < groups.size(); ++i )
			originalWeights.add( unnormalizedWeights.get( groups.get( i ) ) );

		// normalizes the weights (sum == 1) and applies osem-speedup if wanted
		for ( int i = 0; i < groups.size(); ++i )
		{
			final Group< V > group = groups.get( i );

			normalizedWeights.put( group,
				new NormalizingRandomAccessibleInterval< FloatType >(
					unnormalizedWeights.get( group ),
					i,
					originalWeights,
					osemspeedup,
					additionalSmoothBlending,
					maxDiffRange,
					scalingRange,
					new FloatType() ) );
		}

		return normalizedWeights;
	}

	public static < V extends ViewId > void deVirtualize(
			final ImgDataType type,
			final Collection< Group< V > > groups,
			final ImgFactory< FloatType > factory,
			final int cellDim,
			final int maxCacheSize,
			final HashMap< Group< V >, RandomAccessibleInterval< FloatType > > images )
	{
		if ( type == ImgDataType.VIRTUAL )
			return;

		for ( final Group< V > group : groups )
		{
			if ( !images.containsKey( group ) )
				continue;

			RandomAccessibleInterval< FloatType > img = images.get( group );

			if ( type == ImgDataType.CACHED )
				img = FusionTools.cacheRandomAccessibleInterval( img, maxCacheSize, new FloatType(), cellDim );
			else if ( type == ImgDataType.PRECOMPUTED )
				img = FusionTools.copyImg( img, factory );

			images.put( group, img );
		}
	}

	public static < V extends ViewId > Interval fuseGroups(
			final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? extends BasicImgLoader > > spimData,
			final HashMap< Group< V >, RandomAccessibleInterval< FloatType > > tImgs,
			final HashMap< Group< V >, RandomAccessibleInterval< FloatType > > tWeights,
			final HashMap< V, AffineTransform3D > models,
			final Collection< Group< V > > groups,
			final Interval boundingBox,
			final double downsampling,
			final float[] blendingRangeFusion,
			final float[] blendingBorderFusion,
			final float[] blendingRangeDecon,
			final float[] blendingBorderDecon )
	{
		int i = 0;

		// scale the bounding box if necessary
		final Interval bb;
		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( boundingBox, 1.0 / downsampling );
		else
			bb = boundingBox;

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		for ( final Group< V > group : groups )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Transforming group " + (++i) + " of " + groups.size() + " (group=" + group + ")" );

			final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
			final ArrayList< RandomAccessibleInterval< FloatType > > weightsFusion = new ArrayList<>();
			final ArrayList< RandomAccessibleInterval< FloatType > > weightsDecon = new ArrayList<>();

			for ( final V viewId : group.getViews() )
			{
				final BasicImgLoader imgloader = spimData.getSequenceDescription().getImgLoader();
				final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
				vr.updateModel();
				AffineTransform3D model = vr.getModel();

				// adjust the model for downsampling
				if ( !Double.isNaN( downsampling ) )
				{
					model = model.copy();
					TransformVirtual.scaleTransform( model, 1.0 / downsampling );
				}

				// we need to add a copy here since below the model might be modified for downsampled opening
				models.put( viewId, model.copy() );

				// this modifies the model so it maps from a smaller image to the global coordinate space,
				// which applies for the image itself as well as the weights since they also use the smaller
				// input image as reference
				final RandomAccessibleInterval inputImg = TransformView.openDownsampled( imgloader, viewId, model );
				images.add( TransformView.transformView( inputImg, model, bb, MVDeconvolution.minValueImg, 0, 1 ) );

				if ( blendingRangeFusion != null && blendingBorderFusion != null )
				{
					// we need a different blending when virtually fusing the images since a negative
					// value would actually lead to artifacts there
					final float[] rangeFusion = blendingRangeFusion.clone();
					final float[] borderFusion = blendingBorderFusion.clone();

					// adjust both for z-scaling (anisotropy)
					FusionTools.adjustBlending( spimData.getSequenceDescription().getViewDescriptions().get( viewId ), rangeFusion, borderFusion );

					weightsFusion.add( TransformWeight.transformBlending( inputImg, borderFusion, rangeFusion, model, bb ) );
				}
				else
				{
					weightsFusion.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), bb.numDimensions() ), bb ) );
				}

				if ( blendingRangeDecon != null && blendingBorderDecon != null )
				{
					// however, to then run the deconvolution with this data, we want negative values
					// to maximize the usage of image data
					final float[] rangeDecon = blendingRangeDecon.clone();
					final float[] borderDecon = blendingBorderDecon.clone();

					// adjust both for z-scaling (anisotropy)
					FusionTools.adjustBlending( spimData.getSequenceDescription().getViewDescriptions().get( viewId ), rangeDecon, borderDecon );

					weightsDecon.add( TransformWeight.transformBlending( inputImg, borderDecon, rangeDecon, model, bb ) );
				}
				else
				{
					weightsDecon.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), bb.numDimensions() ), bb ) );
				}
			}

			// the fused image per group
			final RandomAccessibleInterval< FloatType > img = new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weightsFusion );

			// the weights used for deconvolution per group
			final RandomAccessibleInterval< FloatType > weight = new CombineWeightsRandomAccessibleInterval( new FinalInterval( dim ), weightsDecon, CombineType.SUM );

			tImgs.put( group, img );
			tWeights.put( group, weight );
		}

		return bb;
	}
}
