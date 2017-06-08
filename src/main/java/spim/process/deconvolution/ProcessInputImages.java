package spim.process.deconvolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.deconvolution.MVDeconvolution;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.FusedWeightsRandomAccessibleInterval;
import spim.process.fusion.transformed.TransformView;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.fusion.transformed.TransformWeight;
import spim.process.fusion.weightedavg.ProcessFusion;
import spim.process.fusion.weightedavg.ProcessVirtual;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class ProcessInputImages< V extends ViewId >
{
	public static int defaultBlendingRangeNumber = 12;
	public static int defaultBlendingBorderNumber = -8;
	public static int cellDim = 64, maxCacheSize = 10000;

	public static enum ImgDataType { VIRTUAL, CACHED, PRECOMPUTED };
	
	final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? extends BasicImgLoader > > spimData;
	final Collection< Group< V > > groups;
	final Interval bb;
	Interval downsampledBB;
	final double downsampling;
	ImgFactory< FloatType > factory = new CellImgFactory<>( 64 );

	final HashMap< Group< V >, Pair< RandomAccessibleInterval< FloatType >, RandomAccessibleInterval< FloatType > > > imgWeights;
	final HashMap< V, AffineTransform3D > models;

	public ProcessInputImages(
			final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? extends BasicImgLoader > > spimData,
			final Collection< Group< V > > groups,
			final Interval bb,
			final double downsampling )
	{
		this.spimData = spimData;
		this.groups = groups;
		this.bb = bb;
		this.downsampling = downsampling;

		this.imgWeights = new HashMap<>();
		this.models = new HashMap<>();
	}

	public ProcessInputImages(
			final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? extends BasicImgLoader > > spimData,
			final Collection< Group< V > > groups,
			Interval bb )
	{
		this( spimData, groups, bb, Double.NaN );
	}

	public Interval getBoundingBox() { return bb; }
	public Interval getDownsampledBoundingBox() { return downsampledBB; }
	public HashMap< V, AffineTransform3D > getDownsampledModels() { return models; }
	public HashMap< Group< V >, Pair< RandomAccessibleInterval< FloatType >, RandomAccessibleInterval< FloatType > > > getImgWeights() { return imgWeights; }

	public void fuseGroups()
	{
		this.downsampledBB = fuseGroups( spimData, imgWeights, models, groups, bb, downsampling );
	}

	public void deVirtualizeImages( final ImgDataType typeImg, final ImgDataType typeWeights )
	{
		if ( typeImg == ImgDataType.VIRTUAL && typeWeights == ImgDataType.VIRTUAL )
			return;

		for ( final Group< V > group : groups )
		{
			if ( !imgWeights.containsKey( group ) )
				continue;

			final Pair< RandomAccessibleInterval< FloatType >, RandomAccessibleInterval< FloatType > > imgWeight = imgWeights.get( group );
			RandomAccessibleInterval< FloatType > img = imgWeight.getA();
			RandomAccessibleInterval< FloatType > weight = imgWeight.getB();

			if ( typeImg == ImgDataType.CACHED )
				img = FusionHelper.cacheRandomAccessibleInterval( img, new FloatType(), cellDim, maxCacheSize );
			else if ( typeImg == ImgDataType.PRECOMPUTED )
				img = FusionHelper.copyImg( img, factory );

			if ( typeWeights == ImgDataType.CACHED )
				weight = FusionHelper.cacheRandomAccessibleInterval( weight, new FloatType(), cellDim, maxCacheSize );
			else if ( typeWeights == ImgDataType.PRECOMPUTED )
				weight = FusionHelper.copyImg( weight, factory );

			imgWeights.put( group, new ValuePair<>( img, weight ) );
		}
	}



	public static < V extends ViewId > Interval fuseGroups(
			final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? extends BasicImgLoader > > spimData,
			final HashMap< Group< V >, Pair< RandomAccessibleInterval< FloatType >, RandomAccessibleInterval< FloatType > > > imgWeights,
			final HashMap< V, AffineTransform3D > models,
			final Collection< Group< V > > groups,
			final Interval boundingBox,
			final double downsampling )
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
			SpimData2.filterMissingViews( spimData, group.getViews() );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Transforming group " + (++i) + " of " + groups.size() + " (group=" + group + ")" );

			if ( group.getViews().size() == 0 )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Group is empty. Continuing with next one." );
				continue;
			}

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

				models.put( viewId, model );

				// we need a different blending when virtually fusing the images since a negative
				// value would actually lead to artifacts there
				final float[] blendingFusion = ProcessFusion.defaultBlendingRange.clone();
				final float[] borderFusion = ProcessFusion.defaultBlendingBorder.clone();

				// however, to then run the deconvolution with this data, we want negative values
				// to maximize the usage of image data
				final float[] blendingDecon = new float[]{ defaultBlendingRangeNumber, defaultBlendingRangeNumber, defaultBlendingRangeNumber };
				final float[] borderDecon = new float[]{ defaultBlendingBorderNumber, defaultBlendingBorderNumber, defaultBlendingBorderNumber };

				// adjust both for z-scaling (anisotropy)
				ProcessVirtual.adjustBlending( spimData.getSequenceDescription().getViewDescriptions().get( viewId ), blendingFusion, borderFusion );
				ProcessVirtual.adjustBlending( spimData.getSequenceDescription().getViewDescriptions().get( viewId ), blendingDecon, borderDecon );

				final RandomAccessibleInterval inputImg = TransformView.openDownsampled( imgloader, viewId, model );

				images.add( TransformView.transformView( inputImg, model, bb, MVDeconvolution.minValueImg, 0, 1 ) );
				weightsFusion.add( TransformWeight.transformBlending( inputImg, borderFusion, blendingFusion, model, bb ) );
				weightsDecon.add( TransformWeight.transformBlending( inputImg, borderDecon, blendingDecon, model, bb ) );
			}

			// the fused image per group
			final RandomAccessibleInterval< FloatType > img = new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weightsFusion );

			// the weights used for deconvolution per group
			final RandomAccessibleInterval< FloatType > weight = new FusedWeightsRandomAccessibleInterval( new FinalInterval( dim ), weightsDecon );

			imgWeights.put( group, new ValuePair<>( img, weight ) );
		}

		return bb;
	}
}
