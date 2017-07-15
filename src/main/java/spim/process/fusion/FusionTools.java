package spim.process.fusion;

import java.util.ArrayList;
import java.util.Collection;

import bdv.util.ConstantRandomAccessible;
import ij.ImagePlus;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.spimdata.explorer.popup.DisplayFusedImagesPopup;
import spim.process.export.DisplayImage;
import spim.process.fusion.FusionHelper.ImgDataType;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.TransformView;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.fusion.transformed.TransformWeight;

public class FusionTools
{
	public static float[] defaultBlendingRange = new float[]{ 40, 40, 40 };
	public static float[] defaultBlendingBorder = new float[]{ 0, 0, 0 };

	public static double[] defaultContentBasedSigma1 = new double[]{ 20, 20, 20 };
	public static double[] defaultContentBasedSigma2 = new double[]{ 40, 40, 40 };

	public static RandomAccessibleInterval< FloatType > fuseVirtual(
			final SpimData spimData,
			final Collection< ? extends ViewId > views,
			final boolean useBlending,
			final Interval boundingBox,
			final double downsampling )
	{
		final Interval bb;

		if ( !Double.isNaN( downsampling ) )
			bb = TransformVirtual.scaleBoundingBox( boundingBox, 1.0 / downsampling );
		else
			bb = boundingBox;

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

		for ( final ViewId viewId : views )
		{
			final ImgLoader imgloader = spimData.getSequenceDescription().getImgLoader();
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();
			AffineTransform3D model = vr.getModel();

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			// this modifies the model so it maps from a smaller image to the global coordinate space,
			// which applies for the image itself as well as the weights since they also use the smaller
			// input image as reference
			final RandomAccessibleInterval inputImg = TransformView.openDownsampled( imgloader, viewId, model );

			images.add( TransformView.transformView( inputImg, model, bb, 0, 1 ) );

			if ( useBlending )
			{
				final float[] blending = defaultBlendingRange.clone();
				final float[] border = defaultBlendingBorder.clone();

				FusionHelper.adjustBlending( spimData.getSequenceDescription().getViewDescription( viewId ), blending, border );

				weights.add( TransformWeight.transformBlending( inputImg, border, blending, model, bb ) );
			}
			else
			{
				weights.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( dim ) ) );
			}

			//images.add( TransformWeight.transformBlending( inputImg, border, blending, model, bb ) );
		}

		return new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights );
	}

	public static ImagePlus displayVirtually( final RandomAccessibleInterval< FloatType > input )
	{
		return display( input, ImgDataType.VIRTUAL, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayVirtually( final RandomAccessibleInterval< FloatType > input, final double min, final double max )
	{
		return display( input, ImgDataType.VIRTUAL, min, max, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCopy( final RandomAccessibleInterval< FloatType > input )
	{
		return display( input, ImgDataType.PRECOMPUTED, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCopy( final RandomAccessibleInterval< FloatType > input, final double min, final double max )
	{
		return display( input, ImgDataType.PRECOMPUTED, min, max, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCached(
			final RandomAccessibleInterval< FloatType > input,
			final int[] cellDim,
			final int maxCacheSize )
	{
		return display( input, ImgDataType.CACHED, cellDim, maxCacheSize );
	}

	public static ImagePlus displayCached(
			final RandomAccessibleInterval< FloatType > input,
			 final double min,
			 final double max,
			final int[] cellDim,
			final int maxCacheSize )
	{
		return display( input, ImgDataType.CACHED, min, max, cellDim, maxCacheSize );
	}

	public static ImagePlus displayCached( final RandomAccessibleInterval< FloatType > input )
	{
		return displayCached( input, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCached( final RandomAccessibleInterval< FloatType > input, final double min, final double max )
	{
		return display( input, ImgDataType.CACHED, min, max, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus display(
			final RandomAccessibleInterval< FloatType > input,
			final ImgDataType imgType )
	{
		return display( input, imgType, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus display(
			final RandomAccessibleInterval< FloatType > input,
			final ImgDataType imgType,
			final int[] cellDim,
			final int maxCacheSize )
	{
		return display( input, imgType, 0, 255, cellDim, maxCacheSize );
	}

	public static ImagePlus display(
			final RandomAccessibleInterval< FloatType > input,
			final ImgDataType imgType,
			final double min,
			final double max,
			final int[] cellDim,
			final int maxCacheSize )
	{
		final RandomAccessibleInterval< FloatType > img;

		if ( imgType == ImgDataType.CACHED )
			img = FusionHelper.cacheRandomAccessibleInterval( input, maxCacheSize, new FloatType(), cellDim );
		else if ( imgType == ImgDataType.PRECOMPUTED )
			img = FusionHelper.copyImg( input, new ImagePlusImgFactory<>(), true );
		else
			img = input;

		// set ImageJ title according to fusion type
		final String title = imgType == ImgDataType.CACHED ? 
				"Fused, Virtual (cached) " : (imgType == ImgDataType.VIRTUAL ? 
						"Fused, Virtual" : "Fused" );

		return DisplayImage.getImagePlusInstance( img, true, title, min, max );
	}
}
