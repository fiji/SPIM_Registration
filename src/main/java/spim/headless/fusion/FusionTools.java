package spim.headless.fusion;

import ij.ImageJ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.headless.registration.TransformationTools;
import spim.process.fusion.weightedavg.ProcessFusion;
import spim.process.fusion.weightedavg.ProcessParalell;
import spim.process.fusion.weights.Blending;
import spim.process.fusion.weights.ContentBased;
import bdv.img.hdf5.Hdf5ImageLoader;

public class FusionTools
{
	public static double getMinRes( final ViewDescription desc, final ImgLoader< ? > imgLoader )
	{
		final VoxelDimensions size = ViewSetupUtils.getVoxelSizeOrLoad( desc.getViewSetup(), desc.getTimePoint(), imgLoader );
		return Math.min( size.dimension( 0 ), Math.min( size.dimension( 1 ), size.dimension( 2 ) ) );
	}

	public static Blending getBlending(
			final Interval interval,
			final ViewDescription desc,
			final ImgLoader< ? > imgLoader )
	{
		final float[] blending = ProcessFusion.defaultBlendingRange.clone();
		final float[] border = ProcessFusion.defaultBlendingBorder.clone();
		
		final float minRes = (float)getMinRes( desc, imgLoader );
		final VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSizeOrLoad( desc.getViewSetup(), desc.getTimePoint(), imgLoader );

		if ( ProcessFusion.defaultAdjustBlendingForAnisotropy )
		{
			for ( int d = 0; d < 2; ++d )
			{
				blending[ d ] /= ( float ) voxelSize.dimension( d ) / minRes;
				border[ d ] /= ( float ) voxelSize.dimension( d ) / minRes;
			}
		}
		
		return new Blending( interval, border, blending );
	}
	
	public static < T extends RealType< T > > ContentBased< T > getContentBased(
			final RandomAccessibleInterval< T > img,
			final ViewDescription desc,
			final ImgLoader< ? > imgLoader,
			final ImgFactory< ComplexFloatType > factory )
	{
		final double[] sigma1 = ProcessFusion.defaultContentBasedSigma1.clone();
		final double[] sigma2 = ProcessFusion.defaultContentBasedSigma2.clone();

		final double minRes = getMinRes( desc, imgLoader );
		final VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSizeOrLoad(
				desc.getViewSetup(), desc.getTimePoint(), imgLoader );

		if ( ProcessFusion.defaultAdjustContentBasedSigmaForAnisotropy )
		{
			for ( int d = 0; d < 2; ++d )
			{
				sigma1[ d ] /= voxelSize.dimension( d ) / minRes;
				sigma2[ d ] /= voxelSize.dimension( d ) / minRes;
			}
		}

		return new ContentBased< T >( img, factory, sigma1, sigma2 );
	}

	public static boolean matches( final Interval interval, final BoundingBox bb, final int downsampling )
	{
		for ( int d = 0; d < interval.numDimensions(); ++d )
			if ( interval.dimension( d ) != bb.getDimensions( downsampling )[ d ] )
				return false;

		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static < T extends RealType< T > > RandomAccessibleInterval< T > getImage(
			final T type,
			ImgLoader< ? > imgLoader,
			final ViewId view,
			final boolean normalize )
	{
		if ( imgLoader instanceof Hdf5ImageLoader )
			imgLoader = ( ( Hdf5ImageLoader ) imgLoader ).getMonolithicImageLoader();

		if ( (RealType)type instanceof FloatType )
			return (RandomAccessibleInterval)imgLoader.getFloatImage( view, normalize );
		else if ( (RealType)type instanceof UnsignedShortType )
			return (RandomAccessibleInterval)imgLoader.getImage( view );
		else
			return null;
	}

	public static List< RandomAccessibleInterval< FloatType > > getFloatImages(
			ImgLoader< ? > imgLoader,
			final Collection< ViewId > viewIds,
			final boolean normalize )
	{
		return getImages( new FloatType(), imgLoader, viewIds, normalize );
	}

	public static List< RandomAccessibleInterval< UnsignedShortType > > getUShortImages(
			ImgLoader< ? > imgLoader,
			final Collection< ViewId > viewIds )
	{
		return getImages( new UnsignedShortType(), imgLoader, viewIds, false );
	}

	public static < T extends RealType< T > > List< RandomAccessibleInterval< T > > getImages(
			final T type,
			ImgLoader< ? > imgLoader,
			final Collection< ViewId > viewIds,
			final boolean normalize )
	{
		final ArrayList< RandomAccessibleInterval< T > > imgs = new ArrayList< RandomAccessibleInterval< T > >();

		for ( final ViewId viewId : viewIds )
			imgs.add( getImage( type, imgLoader, viewId, normalize ) );
		
		return imgs;
	}

	public static AffineTransform3D getTransform( final SpimData spimData, final ViewId viewId )
	{
		spimData.getViewRegistrations().getViewRegistration( viewId ).updateModel();
		return spimData.getViewRegistrations().getViewRegistration( viewId ).getModel();
	}

	public static List< AffineTransform3D > getTransforms( final SpimData spimData, final Collection< ViewId > viewIds )
	{
		final ArrayList< AffineTransform3D > transforms = new ArrayList< AffineTransform3D >();

		for ( final ViewId viewId : viewIds )
			transforms.add( getTransform( spimData, viewId ) );
		
		return transforms;
	}

	public static void testFusion( final SpimData2 spimData )
	{
		TransformationTools.testRegistration( spimData );

		// make a bounding box
		final BoundingBox bb = new BoundingBox(
				new int[]{ 140, 100, 000 },
				new int[]{ 500, 600, 200 } );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );
		Collections.sort( viewIds );

		// fuse in parallel
		Img< UnsignedShortType > fused = ProcessParalell.fuse(
				new UnsignedShortType(),
				new NLinearInterpolatorFactory< UnsignedShortType >(),
				new ArrayImgFactory< UnsignedShortType >(),
				getUShortImages( spimData.getSequenceDescription().getImgLoader(), viewIds ),
				null, //weights,
				getTransforms( spimData, viewIds ),
				bb,
				1,
				null );

		new ImageJ();
		ImageJFunctions.show( fused );
	}

	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90 } ) );

		testFusion( spimData );
	}
}
