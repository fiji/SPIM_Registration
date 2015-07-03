package spim.headless.interestpointdetection;

import java.util.Date;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import spim.process.interestpointdetection.Downsample;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.MultiResolutionImgLoader;

public class DownsampleTools
{
	protected static final int[] ds = { 1, 2, 4, 8 };

	//TODO: make this more clean (XY, Z downsample)
	
	/**
	 * 
	 * @param imgLoader
	 * @param vd
	 * @param t - will be filled if downsampling is performed, otherwise identity transform
	 * @param downsampleXYIndex - specify which downsampling (0 == ?? )
	 * @param downsampleZ - 
	 * @return
	 */
	public static RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > openAndDownsample(
			ImgLoader< ? > imgLoader,
			final ViewDescription vd,
			final AffineTransform3D t,
			final int downsampleXYIndex,
			final int downsampleZ )
	{
		IOFunctions.println(
				"(" + new Date(System.currentTimeMillis()) + "): "
				+ "Requesting Img from ImgLoader (tp=" + vd.getTimePointId() + ", setup=" + vd.getViewSetupId() + ")" );

		int downsampleXY = downsampleXYIndex;

		// downsampleXY == 0 : a bit less then z-resolution
		// downsampleXY == -1 : a bit more then z-resolution
		if ( downsampleXY < 1 )
			downsampleXY = Downsample.downsampleFactor( downsampleXYIndex, downsampleZ, vd.getViewSetup().getVoxelSize() );

		if ( downsampleXY > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in XY " + downsampleXY + "x ..." );

		if ( downsampleZ > 1 )
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() )  + "): Downsampling in Z " + downsampleZ + "x ..." );

		int dsx = downsampleXY;
		int dsy = downsampleXY;
		int dsz = downsampleZ;

		RandomAccessibleInterval< net.imglib2.type.numeric.real.FloatType > input = null;

		if ( Hdf5ImageLoader.class.isInstance( imgLoader ) )
			imgLoader = ( ( Hdf5ImageLoader ) imgLoader ).getMonolithicImageLoader();
		
		if ( ( dsx > 1 || dsy > 1 || dsz > 1 ) && MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			MultiResolutionImgLoader< ? > mrImgLoader = ( MultiResolutionImgLoader< ? > ) imgLoader;

			double[][] mipmapResolutions = mrImgLoader.getMipmapResolutions( vd.getViewSetupId() );
			
			int bestLevel = 0;
			for ( int level = 0; level < mipmapResolutions.length; ++level )
			{
				double[] factors = mipmapResolutions[ level ];
				
				// this fails if factors are not ints
				final int fx = (int)Math.round( factors[ 0 ] );
				final int fy = (int)Math.round( factors[ 1 ] );
				final int fz = (int)Math.round( factors[ 2 ] );
				
				if ( fx <= dsx && fy <= dsy && fz <= dsz && contains( fx, ds ) && contains( fy, ds ) && contains( fz, ds ) )
					bestLevel = level;
			}

			final int fx = (int)Math.round( mipmapResolutions[ bestLevel ][ 0 ] );
			final int fy = (int)Math.round( mipmapResolutions[ bestLevel ][ 1 ] );
			final int fz = (int)Math.round( mipmapResolutions[ bestLevel ][ 2 ] );

			t.set( mrImgLoader.getMipmapTransforms(vd.getViewSetupId())[ bestLevel ] );

			dsx /= fx;
			dsy /= fy;
			dsz /= fz;

			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): " +
					"Using precomputed Multiresolution Images [" + fx + "x" + fy + "x" + fz + "], " +
					"Remaining downsampling [" + dsx + "x" + dsy + "x" + dsz + "]" );

			input = mrImgLoader.getFloatImage( vd, bestLevel, false );
		}
		else
		{
			input = imgLoader.getFloatImage( vd, false );
			t.identity();
		}

		final ImgFactory< net.imglib2.type.numeric.real.FloatType > f = ((Img<net.imglib2.type.numeric.real.FloatType>)input).factory();

		t.set( downsampleXY, 0, 0 );
		t.set( downsampleXY, 1, 1 );
		t.set( downsampleZ, 2, 2 );

		for ( ;dsx > 1; dsx /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ true, false, false } );

		for ( ;dsy > 1; dsy /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, true, false } );

		for ( ;dsz > 1; dsz /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, false, true } );

		return input;
	}

	private static final boolean contains( final int i, final int[] values )
	{
		for ( final int j : values )
			if ( i == j )
				return true;

		return false;
	}

}
