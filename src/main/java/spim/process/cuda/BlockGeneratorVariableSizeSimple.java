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
public class BlockGeneratorVariableSizeSimple implements BlockGenerator< Block >
{
	final long[] numBlocks;

	public BlockGeneratorVariableSizeSimple( final long[] numBlocksDim )
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
	public Block[] divideIntoBlocks( final long[] imgSize, final long[] kernelSize )
	{
		final int numDimensions = imgSize.length;
		
		// compute the effective size & local offset of each block
		// this is the same for all blocks
		/*
		final int[] effectiveSizeGeneral = new int[ numDimensions ];
		final int[] effectiveLocalOffset = new int[ numDimensions ];
		
		for ( int d = 0; d < numDimensions; ++d )
		{
			effectiveSizeGeneral[ d ] = blockSize[ d ] - kernelSize[ d ] + 1;
			
			if ( effectiveSizeGeneral[ d ] <= 0 )
			{
				System.out.println( "Blocksize in dimension " + d + " (" + blockSize[ d ] + ") is smaller than the kernel (" + kernelSize[ d ] + ") which results in an negative effective size: " + effectiveSizeGeneral[ d ] + ". Quitting." );
				return null;
			}
			
			effectiveLocalOffset[ d ] = kernelSize[ d ] / 2;
		}
		
		// compute the amount of blocks needed
		final int[] numBlocks = new int[ numDimensions ];

		for ( int d = 0; d < numDimensions; ++d )
		{
			numBlocks[ d ] = imgSize[ d ] / effectiveSizeGeneral[ d ];
			
			// if the modulo is not 0 we need one more that is only partially useful
			if ( imgSize[ d ] % effectiveSizeGeneral[ d ] != 0 )
				++numBlocks[ d ];
		}
		*/
		// now we instantiate the individual blocks iterating over all dimensions
		// we use the well-known ArrayLocalizableCursor for that
		final LocalizingZeroMinIntervalIterator cursor = new LocalizingZeroMinIntervalIterator( numBlocks );
		final ArrayList< Block > blockList = new ArrayList< Block >();

		final long[] currentBlock = new long[ numDimensions ];

		while ( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.localize( currentBlock );

			// the blocksize
			final long[] blockSize = new long[ numDimensions ];

			// compute the current offset
			final long[] offset = new long[ numDimensions ];
			final long[] effectiveOffset = new long[ numDimensions ];
			final long[] effectiveSize = new long[ numDimensions ];
			final long[] effectiveLocalOffset = new long[ numDimensions ];

			for ( int d = 0; d < numDimensions; ++d )
			{
				if ( numBlocks[ d ] == 1 ) // is there only one block?
				{
					effectiveLocalOffset[ d ] = offset[ d ] = effectiveOffset[ d ] = 0;
					blockSize[ d ] = effectiveSize[ d ] = imgSize[ d ];
				}
				else if ( currentBlock[ d ] == 0 ) // is the first block?
				{
					effectiveLocalOffset[ d ] = offset[ d ] = effectiveOffset[ d ] = 0;
					effectiveSize[ d ] = imgSize[ d ] / numBlocks[ d ];
					blockSize[ d ] = effectiveSize[ d ] + kernelSize[ d ]/2;
				}
				else if ( currentBlock[ d ] < numBlocks[ d ] - 1 ) // it is some block in the middle
				{
					effectiveLocalOffset[ d ] = kernelSize[ d ] / 2;
					effectiveSize[ d ] = imgSize[ d ] / numBlocks[ d ];
					blockSize[ d ] = effectiveSize[ d ] + kernelSize[ d ] - 1;
					effectiveOffset[ d ] = currentBlock[ d ] * effectiveSize[ d ];
					offset[ d ] = effectiveOffset[ d ] - kernelSize[ d ]/2;
				}
				else // is the last block?
				{
					effectiveLocalOffset[ d ] = kernelSize[ d ] / 2;
					effectiveSize[ d ] = imgSize[ d ] / numBlocks[ d ] + imgSize[ d ] % numBlocks[ d ];
					blockSize[ d ] = effectiveSize[ d ] + kernelSize[ d ]/2;
					effectiveOffset[ d ] = currentBlock[ d ] * effectiveSize[ d ];
					offset[ d ] = effectiveOffset[ d ] - kernelSize[ d ]/2;
				}

				if ( effectiveSize[ d ] <= 0 )
				{
					System.out.println( "Blocksize in dimension " + d + " (" + blockSize[ d ] + ") is smaller than the kernel (" + kernelSize[ d ] + ") which results in an negative effective size: " + effectiveSize[ d ] + ". Quitting." );
					return null;
				}
			}

			blockList.add( new Block( blockSize, offset, effectiveSize, effectiveOffset, effectiveLocalOffset, false ) );
			System.out.println( "block " + Util.printCoordinates( currentBlock ) + " offset: " + Util.printCoordinates( offset ) + " effectiveOffset: " + Util.printCoordinates( effectiveOffset ) + " effectiveLocalOffset: " + Util.printCoordinates( effectiveLocalOffset ) + " effectiveSize: " + Util.printCoordinates( effectiveSize )  + " blocksize: " + Util.printCoordinates( blockSize ) );
		}
		
		final Block[] blocks = new Block[ blockList.size() ];
		for ( int i = 0; i < blockList.size(); ++i )
			blocks[ i ] = blockList.get( i );
			
		return blocks;
	}

	public static void main( String[] args )
	{
		new BlockGeneratorVariableSizeSimple( new long[]{ 3, 2, 1 } ).divideIntoBlocks( new long[] { 1025, 1024, 117 }, new long[]{ 17, 17, 4 } );
	}
}
