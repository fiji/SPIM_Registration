package spim.process.deconvolution2;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import bdv.util.ConstantRandomAccessible;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
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

	final int n, numBlocks;
	final int[] blockSize;
	final List< List< Block > > nonInterferingBlocks;

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
		this( service, image, weight, kernel, PSFTYPE.INDEPENDENT, defaultBlockSize, 1 );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final int[] blockSize )
	{
		this( service, image, weight, kernel, PSFTYPE.INDEPENDENT, blockSize, 1 );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final PSFTYPE psfType,
			final int[] blockSize )
	{
		this( service, image, weight, kernel, psfType, blockSize, 1 );
	}

	public DeconView(
			final ExecutorService service,
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final ArrayImg< FloatType, ? > kernel,
			final PSFTYPE psfType,
			final int[] blockSize,
			final int minRequiredBlocks )
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

		this.blockSize = new int[ n ];

		// define the blocksize so that it is one single block
		for ( int d = 0; d < this.blockSize.length; ++d )
			this.blockSize[ d ] = blockSize[ d ];

		final long[] imgSize = new long[ n ];
		final long[] kernelSize = new long[ n ];

		image.dimensions( imgSize );
		kernel.dimensions( kernelSize );

		final BlockGeneratorFixedSizePrecise blockGenerator = new BlockGeneratorFixedSizePrecise( service, Util.int2long( this.blockSize ) );
		final ArrayList< Block > blocks = blockGenerator.divideIntoBlocks( imgSize, kernelSize );

		this.numBlocks = blocks.size();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Number of blocks: " + numBlocks + ", dim=" + Util.printCoordinates( this.blockSize ) );
		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Effective size of each block (due to kernel size) " + Util.printCoordinates( blocks.get( 0 ).getEffectiveSize() ) );

		this.nonInterferingBlocks = BlockSorter.sortBlocksBySmallestFootprint( blocks, new FinalInterval( image ), minRequiredBlocks );
	}

	public RandomAccessibleInterval< FloatType > getImage() { return image; }
	public RandomAccessibleInterval< FloatType > getWeight() { return weight; }
	public DeconViewPSF getPSF() { return psf; }
	public int[] getBlockSize() { return blockSize; }
	public List< List< Block > > getNonInterferingBlocks() { return nonInterferingBlocks; }
	public int getNumBlocks() { return numBlocks; }

	public static void filterBlocksForContent( final ArrayList< Block[] > blocksList )
	{
		final ArrayList< Block[] > newBlocksList = new ArrayList<>();

		for ( final Block[] blocks : blocksList )
		{
			final ArrayList< Block > newBlocks = new ArrayList<>();
		
		}
	}

	public static boolean blockContainsContent( final Block blockStruct, final RandomAccessibleInterval< FloatType > weight )
	{
		for ( final FloatType t : Views.iterable( Views.interval( Views.extendZero( weight ), blockStruct ) ) )
			if ( t.get() != 0.0 )
				return true;

		return false;
	}
}
