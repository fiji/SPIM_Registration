package spim.process.cuda;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import net.imglib2.util.Util;

/**
 * We need to sort blocks in a way so consecutive ones are not influenced by their predecessors
 *
 * @author spreibi
 *
 */
public class BlockSorter
{
	/**
	 * Here are some assumptions:
	 * - the first block is in the top/left/upper... corner
	 * - the effective size of this block was used to compute the number of blocks
	 * - all blocks have the same dimensions, tightly stacked next to each other
	 * - only the effective blocksize of the last blocks in each dimension can be smaller
	 * 
	 * @param blocks
	 * @param psi
	 * @return
	 */
	public static ArrayList< Block[] > sortBlocksBySmallestFootprint( final Block[] blocks, final Interval psi )
	{
		final int n = psi.numDimensions();

		final long[] effectiveBlockSize = blocks[ 0 ].getEffectiveSize();
		final int[] numBlocks = new int[ n ];

		for ( int d = 0; d < n; ++d )
		{
			final long dim = psi.dimension( d );
			numBlocks[ d ] = (int)( dim / blocks[ 0 ].getEffectiveSize()[ d ] );

			// if the modulo is not 0 we need one more that is only partially useful
			if ( dim % blocks[ 0 ].getEffectiveSize()[ d ] != 0 )
				++numBlocks[ d ];
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Number of blocks in each dimension: " + Util.printCoordinates( numBlocks ) );

		final long[] minOffset = new long[ n ];
		blocks[ 0 ].min( minOffset );

		int minDim = -1;
		long minDimSize = Long.MAX_VALUE;

		for ( int d = 0; d < n; ++d )
		{
			long size = 1;

			for ( int e = 0; e < n; ++e )
				if ( e != d )
					size *= numBlocks[ e ];

			if ( size < minDimSize )
			{
				minDimSize = size;
				minDim = d;
			}
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Minimum sum of blocksize memory (" + minDimSize + " blocks) when progressing in dimension: " + minDim );

		final ArrayList< Block[] > noninterferingBlocks = new ArrayList<>();

		int sum = 0;

		// go layer by layer
		for ( int i = 0; i < numBlocks[ minDim ]; ++i )
		{
			final Block[] newBlocks = new Block[ (int)minDimSize ];
			int j = 0;
			long offset = minOffset[ minDim ] + i * effectiveBlockSize[ minDim ];

			for ( final Block block : blocks )
			{
				if ( block.min( minDim ) == offset )
				{
					newBlocks[ j++ ] = block;
					++sum;
					System.out.println( Util.printInterval( block ) );
				}
				System.out.println();
			}

			noninterferingBlocks.add( newBlocks );
		}

		if ( sum != blocks.length )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): ERROR, could not sort blocks, something is wrong, must keep them all. This is not good." );
			noninterferingBlocks.clear();
			noninterferingBlocks.add( blocks );
		}

		return noninterferingBlocks;
	}
}
