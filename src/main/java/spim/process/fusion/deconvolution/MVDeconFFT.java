package spim.process.fusion.deconvolution;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.postprocessing.deconvolution2.LRInput;
import net.imglib2.Cursor;
import net.imglib2.algorithm.fft2.FFTConvolution;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import spim.Threads;
import spim.process.cuda.Block;
import spim.process.cuda.BlockGeneratorFixedSizePrecise;
import spim.process.cuda.CUDAFourierConvolution;

public class MVDeconFFT
{
	public static enum PSFTYPE { OPTIMIZATION_II, OPTIMIZATION_I, EFFICIENT_BAYESIAN, INDEPENDENT };
	
	public static CUDAFourierConvolution cuda = null;
	
	private Img< FloatType > image, weight, kernel1, kernel2;
	Img< FloatType > viewContribution = null;
	FFTConvolution<FloatType> fftConvolution1, fftConvolution2;
	protected int numViews = 0;

	final protected ExecutorService service;
	PSFTYPE iterationType;
	ArrayList< MVDeconFFT > views;

	final int n;

	final boolean useBlocks, useCUDA, useCPU;
	final int[] blockSize, deviceList;
	final int device0, numDevices;
	final Block[] blocks;
	final ImgFactory< FloatType > factory;
	/**
	 * Used to determine if the Convolutions already have been computed for the current iteration
	 */
	int i = -1;

	public MVDeconFFT(
			final Img< FloatType > image,
			final Img< FloatType > weight,
			final Img< FloatType > kernel,
			final int[] deviceList, final boolean useBlocks, final int[] blockSize )
	{
		this.image = image;
		this.kernel1 = kernel;
		this.weight = weight;
		this.n = image.numDimensions();
		this.service = FFTConvolution.createExecutorService( Threads.numThreads() );

		this.deviceList = deviceList;
		this.device0 = deviceList[ 0 ];
		this.numDevices = deviceList.length;

		// figure out if we need GPU and/or CPU
		boolean anyGPU = false;
		boolean anyCPU = false;
		
		for ( final int i : deviceList )
		{
			if ( i >= 0 )
				anyGPU = true;
			else if ( i == -1 )
				anyCPU = true;
		}

		this.useCUDA = anyGPU;
		this.useCPU = anyCPU;

		if ( useBlocks )
		{
			this.useBlocks = true;

			// define the blocksize so that it is one single block
			this.blockSize = new int[ n ];

			for ( int d = 0; d < this.blockSize.length; ++d )
				this.blockSize[ d ] = blockSize[ d ];

			final long[] imgSize = new long[ n ];
			final long[] kernelSize = new long[ n ];

			image.dimensions( imgSize );
			kernel.dimensions( kernelSize );

			final BlockGeneratorFixedSizePrecise blockGenerator = new BlockGeneratorFixedSizePrecise( Util.int2long( this.blockSize ) );
			this.blocks = blockGenerator.divideIntoBlocks( imgSize, kernelSize );

			IOFunctions.println( "Number of blocks: " + this.blocks.length );

			this.factory = new ArrayImgFactory< FloatType >();
		}
		else if ( this.useCUDA ) // and no blocks, i.e. one big block
		{
			this.useBlocks = true;

			// define the blocksize so that it is one single block
			this.blockSize = new int[ n ];

			for ( int d = 0; d < this.blockSize.length; ++d )
				this.blockSize[ d ] = (int)image.dimension( d ) + (int)kernel.dimension( d ) - 1;

			final long[] imgSize = new long[ n ];
			final long[] kernelSize = new long[ n ];

			image.dimensions( imgSize );
			kernel.dimensions( kernelSize );

			final BlockGeneratorFixedSizePrecise blockGenerator = new BlockGeneratorFixedSizePrecise( Util.int2long( this.blockSize ) );
			this.blocks = blockGenerator.divideIntoBlocks( imgSize, kernelSize );

			IOFunctions.println( "Number of blocks: " + this.blocks.length + " (1 single block for CUDA processing)." );

			this.factory = new ArrayImgFactory< FloatType >();
		}
		else
		{
			this.blocks = null;
			this.blockSize = null;
			this.factory = null;
			this.useBlocks = false;
		}
	}

	/**
	 * @param numViews - the number of views in the acquisition, determines the exponential of the kernel
	 */
	protected void setNumViews( final int numViews ) { this.numViews = numViews; }
	
	/**
	 * This method is called once all views are added to the {@link LRInput}
	 */
	protected void init( final PSFTYPE iterationType, final ArrayList< MVDeconFFT > views )
	{		
		// normalize kernel so that sum of all pixels == 1
		AdjustInput.normImage( kernel1 );

		this.iterationType = iterationType;
		this.views = views;

		if ( numViews == 0 )
		{
			System.out.println( "Warning, numViews was not set." );
			numViews = 1;
		}
		
		if ( numViews == 1 || iterationType == PSFTYPE.INDEPENDENT )
		{
			// compute the inverted kernel (switch dimensions)
			this.kernel2 = computeInvertedKernel( this.kernel1 );
		}
		else if ( iterationType == PSFTYPE.EFFICIENT_BAYESIAN )
		{
			// compute the compound kernel P_v^compound of the efficient bayesian multi-view deconvolution
			// for the current view \phi_v(x_v)
			//
			// P_v^compound = P_v^{*} prod{w \in W_v} P_v^{*} \ast P_w \ast P_w^{*}
			
			// we first get P_v^{*} -> {*} refers to the inverted coordinates
			final Img< FloatType > tmp = computeInvertedKernel( this.kernel1.copy() );

			// now for each view: w \in W_v
			for ( final MVDeconFFT view : views )
			{
				if ( view != this )
				{
					// convolve first P_v^{*} with P_w
					Img< FloatType > input = computeInvertedKernel( this.kernel1 );
					Img< FloatType > kernel = view.kernel1;
					Img< FloatType > output = input.factory().create( input, input.firstElement() );

					final FFTConvolution< FloatType > conv1 = new FFTConvolution< FloatType >(
							Views.extendZero( input ),
							input,
							Views.extendZero( kernel ),
							kernel,
							output,
							new ArrayImgFactory< ComplexFloatType >() );

					conv1.setExecutorService( service );
					conv1.setKeepImgFFT( false );
					conv1.convolve();

					// and now convolve the result with P_w^{*}
					input = output;
					kernel = computeInvertedKernel( view.kernel1 );
					output = input.factory().create( input, input.firstElement() );
					final FFTConvolution< FloatType > conv2 = new FFTConvolution< FloatType >(
							Views.extendZero( input ),
							input,
							Views.extendZero( kernel ),
							kernel,
							output,
							new ArrayImgFactory< ComplexFloatType >() );

					conv2.setExecutorService( service );
					conv2.setKeepImgFFT( false );
					conv2.convolve();

					// multiply the result with P_v^{*} yielding the compound kernel
					final Cursor< FloatType > cursor = tmp.cursor();
					for ( final FloatType t : output )
					{
						cursor.fwd();
						cursor.get().set( t.get() * cursor.get().get() );
					}
				}
			}

			// norm the compound kernel
			AdjustInput.normImage( tmp );

			// set it as kernel2 of the deconvolution
			this.kernel2 = ( tmp );
		}
		else if ( iterationType == PSFTYPE.OPTIMIZATION_I )
		{
			// compute the simplified compound kernel P_v^compound of the efficient bayesian multi-view deconvolution
			// for the current view \phi_v(x_v)
			//
			// P_v^compound = P_v^{*} prod{w \in W_v} P_v^{*} \ast P_w

			// we first get P_v^{*} -> {*} refers to the inverted coordinates
			final Img< FloatType > tmp = ( this.kernel1.copy() );

			// now for each view: w \in W_v
			for ( final MVDeconFFT view : views )
			{
				if ( view != this )
				{
					Img< FloatType > input = this.kernel1;
					Img< FloatType > kernel = computeInvertedKernel( view.kernel1 );
					Img< FloatType > output = input.factory().create( input, input.firstElement() );

					final FFTConvolution< FloatType > conv = new FFTConvolution< FloatType >(
							Views.extendZero( input ),
							input,
							Views.extendZero( kernel ),
							kernel,
							output,
							new ArrayImgFactory< ComplexFloatType >() );

					conv.setExecutorService( service );
					conv.setKeepImgFFT( false );
					conv.convolve();

					// multiply with the kernel
					final Cursor< FloatType > cursor = tmp.cursor();
					for ( final FloatType t : output )
					{
						cursor.fwd();
						cursor.get().set( t.get() * cursor.get().get() );
					}
				}
			}

			// norm the compound kernel
			AdjustInput.normImage( tmp );

			// compute the inverted kernel
			this.kernel2 = computeInvertedKernel( tmp );
		}
		else //if ( iterationType == PSFTYPE.OPTIMIZATION_II )
		{
			// compute the squared kernel and its inverse
			final Img< FloatType > exponentialKernel = computeExponentialKernel( this.kernel1, numViews );

			// norm the squared kernel
			AdjustInput.normImage( exponentialKernel );

			// compute the inverted squared kernel
			this.kernel2 = computeInvertedKernel( exponentialKernel );	
		}
		
		if ( useCPU )
		{
			if ( useBlocks )
			{
				final Img< FloatType > block = factory.create( blockSize, new FloatType() );

				this.fftConvolution1 = new FFTConvolution< FloatType >( block, this.kernel1 );
				this.fftConvolution1.setExecutorService( service );
				this.fftConvolution1.setKeepImgFFT( false );
				
				this.fftConvolution2 = new FFTConvolution< FloatType >( block, this.kernel2 );
				this.fftConvolution2.setExecutorService( service );
				this.fftConvolution2.setKeepImgFFT( false );
			}
			else
			{
				this.fftConvolution1 = new FFTConvolution< FloatType >( this.image, this.kernel1 );
				this.fftConvolution1.setExecutorService( service );
				this.fftConvolution1.setKeepImgFFT( false );
				
				this.fftConvolution2 = new FFTConvolution< FloatType >( this.image, this.kernel2 );
				this.fftConvolution2.setExecutorService( service );
				this.fftConvolution2.setKeepImgFFT( false );
			}
		}
		else
		{
			this.fftConvolution1 = null;
			this.fftConvolution2 = null;
		}
	}
	
	public static Img<FloatType> computeExponentialKernel( final Img<FloatType> kernel, final int numViews )
	{
		final Img<FloatType> exponentialKernel = kernel.copy();

		for ( final FloatType f : exponentialKernel )
			f.set( pow( f.get(), numViews ) );

		return exponentialKernel;
	}

	public static Img< FloatType > computeInvertedKernel( final Img< FloatType > kernel )
	{
		final Img< FloatType > invKernel = kernel.copy();

		for ( int d = 0; d < invKernel.numDimensions(); ++d )
			Mirror.mirror( invKernel, d, Threads.numThreads() );

		return invKernel;
	}

	final private static float pow( final float value, final int power )
	{
		float result = value;

		for ( int i = 1; i < power; ++i )
			result *= value;

		return result;
	}

	public void setImage( final Img< FloatType > image )
	{
		this.image = image;
		setCurrentIteration( -1 );
	}

	public void setWeight( final Img< FloatType > weight ) { this.weight = weight; }

	public void setKernel( final Img< FloatType > kernel ) 
	{
		this.kernel1 = kernel;
		init( iterationType, views );
		setCurrentIteration( -1 );
	}

	public Img<FloatType> getImage() { return image; }
	public Img<FloatType> getWeight() { return weight; }
	public Img<FloatType> getKernel1() { return kernel1; }
	public Img<FloatType> getKernel2() { return kernel2; }
	
	public void setCurrentIteration( final int i ) { this.i = i; }
	public int getCurrentIteration() { return i; }

	/**
	 * convolves the image with kernel1
	 * 
	 * @param image - the image to convolve with
	 * @return
	 */
	public Img< FloatType > convolve1( final Img< FloatType > image )
	{
		if ( useCPU )
		{
			if ( useBlocks )
			{
				final Img< FloatType > result = image.factory().create( image, new FloatType() );
				final Img< FloatType > block = factory.create( blockSize, new FloatType() );

				for ( int i = 0; i < blocks.length; ++i )
					MVDeconFFTThreads.convolve1BlockCPU( blocks[ i ], image, result, block, fftConvolution1, i );

				return result;
			}
			else
			{
				//IJ.log( "Using CPU only to compute as one block ... " );
				final Img< FloatType > result = image.factory().create( image, new FloatType() );

				long time = System.currentTimeMillis();
				final FFTConvolution< FloatType > fftConv = fftConvolution1;
				fftConv.setImg( image );
				fftConv.setOutput( result );
				fftConv.convolve();
				System.out.println( " image: compute " + (System.currentTimeMillis() - time) );

				return result;
			}
		}
		else if ( useCUDA && numDevices == 1 )
		{
			final Img< FloatType > result = image.factory().create( image, new FloatType() );
			final Img< FloatType > block = factory.create( blockSize, new FloatType() );

			for ( int i = 0; i < blocks.length; ++i )
				MVDeconFFTThreads.convolve1BlockCUDA( blocks[ i ], device0, image, result, block, kernel1, i );

			return result;
		}
		else
		{
			final Img< FloatType > result = image.factory().create( image, new FloatType() );

			final AtomicInteger ai = new AtomicInteger();
			final Thread[] threads = new Thread[ deviceList.length ];

			for ( int i = 0; i < deviceList.length; ++i )
				threads[ i ] = MVDeconFFTThreads.getCUDAThread1( ai, blocks, blockSize, image, result, deviceList[ i ], kernel1 );

			for ( int ithread = 0; ithread < threads.length; ++ithread )
				threads[ ithread ].start();

			try
			{
				for ( int ithread = 0; ithread < threads.length; ++ithread )
					threads[ ithread ].join();
			}
			catch (InterruptedException ie)
			{
				throw new RuntimeException(ie);
			}

			return result;
		}
	}

	/**
	 * convolves the image with kernel2 (inverted kernel1)
	 * 
	 * @param image - the image to convolve with
	 * @return
	 */
	public Img< FloatType > convolve2( final Img< FloatType > image )
	{
		if ( useCPU )
		{
			if ( useBlocks )
			{
				final Img< FloatType > result = image.factory().create( image, new FloatType() );
				final Img< FloatType > block = factory.create( blockSize, new FloatType() );

				for ( int i = 0; i < blocks.length; ++i )
					MVDeconFFTThreads.convolve2BlockCPU( blocks[ i ], image, result, block, fftConvolution2 );

				return result;
			}
			else
			{
				final Img< FloatType > result = image.factory().create( image, new FloatType() );

				final FFTConvolution< FloatType > fftConv = fftConvolution2;
				fftConv.setImg( image );
				fftConv.setOutput( result );
				fftConv.convolve();

				return result;
			}
		}
		else if ( useCUDA && numDevices == 1 )
		{
			final Img< FloatType > result = image.factory().create( image, new FloatType() );
			final Img< FloatType > block = factory.create( blockSize, new FloatType() );

			for ( int i = 0; i < blocks.length; ++i )
				MVDeconFFTThreads.convolve2BlockCUDA( blocks[ i ], device0, image, result, block, kernel2 );

			return result;
		}
		else
		{
			final Img< FloatType > result = image.factory().create( image, new FloatType() );

			final AtomicInteger ai = new AtomicInteger();
			final Thread[] threads = new Thread[ deviceList.length ];
			
			for ( int i = 0; i < deviceList.length; ++i )
				threads[ i ] = MVDeconFFTThreads.getCUDAThread2( ai, blocks, blockSize, image, result, deviceList[ i ], kernel2 );

			for ( int ithread = 0; ithread < threads.length; ++ithread )
				threads[ ithread ].start();

			try
			{
				for ( int ithread = 0; ithread < threads.length; ++ithread )
					threads[ ithread ].join();
			}
			catch (InterruptedException ie)
			{
				throw new RuntimeException(ie);
			}

			return result;
		}
	}
}
