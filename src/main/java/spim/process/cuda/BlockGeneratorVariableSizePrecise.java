package spim.process.cuda;

import java.util.ArrayList;

import net.imglib2.iterator.LocalizingZeroMinIntervalIterator;
import net.imglib2.util.Util;

/**
 * This BlockGenerator only cares that the overlap within the image is accounted for, not about
 * an outofbounds strategy.
 * 
 * @author Stephan Preibisch
 */
public class BlockGeneratorVariableSizePrecise implements BlockGenerator< Block >
{
	final int[] numBlocks;

	public BlockGeneratorVariableSizePrecise( final int[] numBlocksDim )
	{
		this.numBlocks = numBlocksDim;
	}

	/**
	 * Divides an image into blocks
	 * 
	 * @param imgSize - the size of the image
	 * @param kernelSize - the size of the kernel (has to be odd!)
	 * @return
	 */
	public Block[] divideIntoBlocks( final int[] imgSize, final int[] kernelSize )
	{
		final int numDimensions = imgSize.length;

		// now we instantiate the individual blocks iterating over all dimensions
		// we use the well-known ArrayLocalizableCursor for that
		final LocalizingZeroMinIntervalIterator cursor = new LocalizingZeroMinIntervalIterator( numBlocks );
		final ArrayList< Block > blockList = new ArrayList< Block >();

		final int[] currentBlock = new int[ numDimensions ];

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.localize( currentBlock );

			// the blocksize
			final int[] blockSize = new int[ numDimensions ];

			// compute the current offset
			final int[] offset = new int[ numDimensions ];
			final int[] effectiveOffset = new int[ numDimensions ];
			final int[] effectiveSize = new int[ numDimensions ];
			final int[] effectiveLocalOffset = new int[ numDimensions ];

			for ( int d = 0; d < numDimensions; ++d )
			{
				effectiveLocalOffset[ d ] = kernelSize[ d ] / 2;
				effectiveSize[ d ] = currentBlock[ d ] == numBlocks[ d ] - 1 ? imgSize[ d ] / numBlocks[ d ] : imgSize[ d ] / numBlocks[ d ] + imgSize[ d ] % numBlocks[ d ];
				blockSize[ d ] = effectiveSize[ d ] + kernelSize[ d ] - 1;
				effectiveOffset[ d ] = currentBlock[ d ] * effectiveSize[ d ];
				offset[ d ] = effectiveOffset[ d ] - kernelSize[ d ]/2;

				if ( effectiveSize[ d ] <= 0 )
				{
					System.out.println( "Blocksize in dimension " + d + " (" + blockSize[ d ] + ") is smaller than the kernel (" + kernelSize[ d ] + ") which results in an negative effective size: " + effectiveSize[ d ] + ". Quitting." );
					return null;
				}
			}

			blockList.add( new Block( blockSize, offset, effectiveSize, effectiveOffset, effectiveLocalOffset, true ) );
			System.out.println( "block " + Util.printCoordinates( currentBlock ) + " offset: " + Util.printCoordinates( offset ) + " effectiveOffset: " + Util.printCoordinates( effectiveOffset ) + " effectiveLocalOffset: " + Util.printCoordinates( effectiveLocalOffset ) + " effectiveSize: " + Util.printCoordinates( effectiveSize )  + " blocksize: " + Util.printCoordinates( blockSize ) );
		}
		
		final Block[] blocks = new Block[ blockList.size() ];
		for ( int i = 0; i < blockList.size(); ++i )
			blocks[ i ] = blockList.get( i );
			
		return blocks;
	}

	public static void main( String[] args )
	{
		new BlockGeneratorVariableSizePrecise( new int[]{ 3, 2, 1 } ).divideIntoBlocks( new int[] { 1025, 1024, 117 }, new int[]{ 17, 17, 5 } );
	}
}
