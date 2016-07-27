package spim.process.fusion.deconvolution;

import java.util.concurrent.atomic.AtomicInteger;

import net.imglib2.algorithm.fft2.FFTConvolution;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import spim.process.cuda.Block;
import spim.process.interestpointdetection.DifferenceOfGaussianCUDA.CUDAOutput;

public class MVDeconFFTThreads
{
	final protected static void convolve1BlockCPU(
			final Block blockStruct, final Img< FloatType > image, final Img< FloatType > result,
			final Img< FloatType > block, final FFTConvolution< FloatType > fftConvolution1, final int i )
	{
		long time = System.currentTimeMillis();
		blockStruct.copyBlock( Views.extendMirrorSingle( image ), block );
		System.out.println( " block " + i + "(CPU): copy " + (System.currentTimeMillis() - time) );

		time = System.currentTimeMillis();
		fftConvolution1.setImg( block );
		fftConvolution1.setOutput( block );
		fftConvolution1.convolve();
		System.out.println( " block " + i + "(CPU): compute " + (System.currentTimeMillis() - time) );

		time = System.currentTimeMillis();
		blockStruct.pasteBlock( result, block );
		System.out.println( " block " + i + "(CPU): paste " + (System.currentTimeMillis() - time) );
	}
	
	final protected static void convolve2BlockCPU(
			final Block blockStruct, final Img< FloatType > image, final Img< FloatType > result,
			final Img< FloatType > block, final FFTConvolution< FloatType > fftConvolution2 )
	{
		// ratio outside of the deconvolved space (psi) is 1
		blockStruct.copyBlock( Views.extendValue( image, new FloatType( 1.0f ) ), block );

		fftConvolution2.setImg( block, block );
		fftConvolution2.setOutput( block );
		fftConvolution2.convolve();
		
		blockStruct.pasteBlock( result, block );
	}
	
	@SuppressWarnings("unchecked")
	final protected static void convolve1BlockCUDA(
			final Block blockStruct, final int deviceId, final Img< FloatType > image,
			final Img< FloatType > result, final Img< FloatType > block, final Img< FloatType > kernel1, final int i )
	{
		long time = System.currentTimeMillis();
		blockStruct.copyBlock( Views.extendMirrorSingle( image ), block );
		System.out.println( " block " + i + "(CPU  " + deviceId + "): copy " + (System.currentTimeMillis() - time) );

		// convolve block with kernel1 using CUDA
		time = System.currentTimeMillis();
		final float[] blockF = ((FloatArray)((ArrayImg< net.imglib2.type.numeric.real.FloatType, ? > )block).update( null ) ).getCurrentStorageArray();
		final float[] kernel1F = ((FloatArray)((ArrayImg< net.imglib2.type.numeric.real.FloatType, ? > )kernel1).update( null ) ).getCurrentStorageArray();
		
		MVDeconFFT.cuda.convolution3DfftCUDAInPlace(
				blockF, getCUDACoordinates( CUDAOutput.getImgSizeInt( block ) ),
				kernel1F, getCUDACoordinates( CUDAOutput.getImgSizeInt( kernel1 ) ),
				deviceId );
		System.out.println( " block " + i + "(CUDA " + deviceId + "): compute " + (System.currentTimeMillis() - time) );

		time = System.currentTimeMillis();
		blockStruct.pasteBlock( result, block );
		System.out.println( " block " + i + "(CPU  " + deviceId + "): paste " + (System.currentTimeMillis() - time) );
	}

	@SuppressWarnings("unchecked")
	final protected static void convolve2BlockCUDA(
			final Block blockStruct, final int deviceId, final Img< FloatType > image,
			final Img< FloatType > result, final Img< FloatType > block, final Img< FloatType > kernel2 )
	{
		// ratio outside of the deconvolved space (psi) is 1
		blockStruct.copyBlock( Views.extendValue( image, new FloatType( 1.0f ) ), block );

		// convolve block with kernel2 using CUDA
		final float[] blockF = ((FloatArray)((ArrayImg< net.imglib2.type.numeric.real.FloatType, ? > )block).update( null ) ).getCurrentStorageArray();
		final float[] kernel2F = ((FloatArray)((ArrayImg< net.imglib2.type.numeric.real.FloatType, ? > )kernel2).update( null ) ).getCurrentStorageArray();

		MVDeconFFT.cuda.convolution3DfftCUDAInPlace(
				blockF, getCUDACoordinates( CUDAOutput.getImgSizeInt( block ) ),
				kernel2F, getCUDACoordinates( CUDAOutput.getImgSizeInt( kernel2 ) ),
				deviceId );

		blockStruct.pasteBlock( result, block );
	}

	final protected static Thread getCUDAThread1(
			final AtomicInteger ai, final ImgFactory< FloatType > blockFactory, final Block[] blocks, final int[] blockSize,
			final Img< FloatType > image, final Img< FloatType > result, final int deviceId, final Img< FloatType > kernel1 )
	{
		final Thread cudaThread1 = new Thread( new Runnable()
		{
			public void run()
			{
				final Img< FloatType > block = blockFactory.create( Util.int2long( blockSize ), new FloatType() );

				int i;

				while ( ( i = ai.getAndIncrement() ) < blocks.length )
					convolve1BlockCUDA( blocks[ i ], deviceId, image, result, block, kernel1, i );
			}
		});
		
		return cudaThread1;
	}

	final protected static Thread getCUDAThread2(
			final AtomicInteger ai, final ImgFactory< FloatType > blockFactory, final Block[] blocks, final int[] blockSize,
			final Img< FloatType > image, final Img< FloatType > result, final int deviceId, final Img< FloatType > kernel2 )
	{
		final Thread cudaThread2 = new Thread( new Runnable()
		{
			public void run()
			{
				final Img< FloatType > block = blockFactory.create( Util.int2long( blockSize ), new FloatType() );

				int i;

				while ( ( i = ai.getAndIncrement() ) < blocks.length )
					convolve2BlockCUDA( blocks[ i ], deviceId, image, result, block, kernel2 );
			}
		});
		
		return cudaThread2;
	}

	private final static int[] getCUDACoordinates( final int[] c )
	{
		final int[] cuda = new int[ c.length ];
		
		for ( int d = 0; d < c.length; ++d )
			cuda[ c.length - d - 1 ] = c[ d ];
		
		return cuda;
	}
}
