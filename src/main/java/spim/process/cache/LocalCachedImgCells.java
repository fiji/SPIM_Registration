/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package spim.process.cache;

import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
import net.imglib2.img.cell.AbstractCells;
import net.imglib2.img.list.AbstractLongListImg;
import net.imglib2.util.Fraction;
import net.imglib2.util.IntervalIndexer;

public class LocalCachedImgCells< A > extends AbstractCells< A, LocalCachedCell< A >, LocalCachedImgCells< A >.CachedCells >
{
	public static interface CellCache< A >
	{
		/**
		 * Get the cell at a specified index.
		 *
		 * @return cell at index or null if the cell is not in the cache.
		 */
		public LocalCachedCell< A > get( final long index );

		/**
		 * Load a cell into memory (eventually) and put it into the cache at the
		 * specified index. Depending on the implementation, loading may be
		 * asynchronous, so the {@link VolatileAccess} of the returned cell may
		 * be invalid for a while.
		 *
		 * @param index
		 *            cell is stored at this index in the cache.
		 * @param cellDims
		 *            dimensions of the cell.
		 * @param cellMin
		 *            offset of the cell in image coordinates.
		 * @return cell at index
		 */
		public LocalCachedCell< A > load( final long index, final int[] cellDims, final long[] cellMin );
	}

	protected final CachedCells cells;

	protected final CellCache< A > cache;

	public LocalCachedImgCells( final CellCache< A > cache, final Fraction entitiesPerPixel, final long[] dimensions, final int[] cellDimensions )
	{
		super( entitiesPerPixel, dimensions, cellDimensions );
		this.cache = cache;
		cells = new CachedCells( numCells );
	}

	@Override
	protected CachedCells cells()
	{
		return cells;
	}

	public class CachedCells extends AbstractLongListImg< LocalCachedCell< A > >
	{
		protected CachedCells( final long[] dim )
		{
			super( dim );
		}

		@Override
		protected LocalCachedCell< A > get( final long index )
		{
			final LocalCachedCell< A > cell = cache.get( index );
			if ( cell != null )
				return cell;
			final long[] cellGridPosition = new long[ n ];
			final long[] cellMin = new long[ n ];
			final int[] cellDims  = new int[ n ];
			IntervalIndexer.indexToPosition( index, dimension, cellGridPosition );
			getCellDimensions( cellGridPosition, cellMin, cellDims );
			return cache.load( index, cellDims, cellMin );
		}

		@Override
		public Img< LocalCachedCell< A > > copy()
		{
			throw new UnsupportedOperationException( "Not supported" );
		}

		@Override
		protected void set( final long index, final LocalCachedCell< A > value )
		{
			throw new UnsupportedOperationException( "Not supported" );
		}

		@Override
		public ImgFactory< LocalCachedCell< A > > factory()
		{
			throw new UnsupportedOperationException( "Not supported" );
		}
	}
}
