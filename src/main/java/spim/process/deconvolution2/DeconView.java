package spim.process.deconvolution2;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import bdv.util.ConstantRandomAccessible;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import spim.process.cuda.Block;
import spim.process.cuda.BlockGeneratorFixedSizePrecise;
import spim.process.cuda.BlockSorter;
import spim.process.deconvolution2.DeconViewPSF.PSFTYPE;

/**
 * One view for the multiview deconvolution, contains image, weight, and PSFs
 *
 * @author stephan.preibisch@gmx.de
 *
 */
public class DeconView
{
	public static int[] defaultBlockSize = new int[]{ 384, 384, 384 };

	final DeconViewPSF psf;
	final RandomAccessibleInterval< FloatType > image, weight;

	final int n;

	final boolean useCUDA, useCPU;
	final int[] blockSize, deviceList;
	final int device0, numDevices;
	final Block[] blocks;
	final ArrayList< Block[] > nonInterferingBlocks;

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final ArrayImg< FloatType, ? > kernel )
	{
		this(
				service,
				image,
				Views.interval(
						new ConstantRandomAccessible< FloatType >(
								new FloatType( 1 ),
								image.numDimensions() ),
						new FinalInterval( image ) ),
				kernel );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel )
	{
		this( service, image, weight, kernel, PSFTYPE.INDEPENDENT, new ArrayImgFactory<>(), new int[]{ -1 }, defaultBlockSize );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final int[] blockSize )
	{
		this( service, image, weight, kernel, PSFTYPE.INDEPENDENT, new ArrayImgFactory<>(), new int[]{ -1 }, blockSize );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final PSFTYPE psfType,
			final int[] blockSize )
	{
		this( service, image, weight, kernel, psfType, new ArrayImgFactory<>(), new int[]{ -1 }, blockSize );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final PSFTYPE psfType,
			final int[] blockSize,
			final ImgFactory< FloatType > blockFactory )
	{
		this( service, image, weight, kernel, psfType, blockFactory, new int[]{ -1 }, blockSize );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final PSFTYPE psfType,
			final ImgFactory< FloatType > blockFactory,
			final int[] deviceList,
			final int[] blockSize )
	{
		this.n = image.numDimensions();
		this.psf = new DeconViewPSF( kernel, psfType );

		if ( Views.isZeroMin( image ) )
			this.image = image;
		else
			this.image = Views.zeroMin( image );

		if ( Views.isZeroMin( weight ) )
			this.weight = weight;
		else
			this.weight = Views.zeroMin( weight );

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

		if ( !this.useCPU && !this.useCUDA )
			throw new RuntimeException( "No computing devices selected." );

		this.blockSize = new int[ n ];

		// define the blocksize so that it is one single block
		for ( int d = 0; d < this.blockSize.length; ++d )
			this.blockSize[ d ] = blockSize[ d ];

		final long[] imgSize = new long[ n ];
		final long[] kernelSize = new long[ n ];

		image.dimensions( imgSize );
		kernel.dimensions( kernelSize );

		final BlockGeneratorFixedSizePrecise blockGenerator = new BlockGeneratorFixedSizePrecise( service, Util.int2long( this.blockSize ) );
		this.blocks = blockGenerator.divideIntoBlocks( imgSize, kernelSize );

		IOFunctions.println( "Number of blocks: " + this.blocks.length + ", dim=" + Util.printCoordinates( this.blockSize ) );
		IOFunctions.println( "Effective size of each block (due to kernel size) " + Util.printCoordinates( this.blocks[ 0 ].getEffectiveSize() ) );

		this.nonInterferingBlocks = BlockSorter.sortBlocksBySmallestFootprint( blocks, new FinalInterval( image ) );
	}

	public RandomAccessibleInterval< FloatType > getImage() { return image; }
	public RandomAccessibleInterval< FloatType > getWeight() { return weight; }
	public DeconViewPSF getPSF() { return psf; }
	public int[] getBlockSize() { return blockSize; }
	public Block[] getBlocks1() { return blocks; }
	public ArrayList< Block[] > getNonInterferingBlocks() { return nonInterferingBlocks; }
	public int getNumBlocks() { return blocks.length; }
}
