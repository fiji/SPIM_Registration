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

import bdv.cache.CacheControl;
import bdv.cache.CacheHints;
import bdv.cache.CacheIoTiming;
import bdv.cache.LoadingStrategy;
import bdv.cache.LoadingVolatileCache;
import bdv.cache.VolatileCacheValueLoader;
import bdv.cache.WeakSoftCache;
import bdv.cache.util.BlockingFetchQueues;
import net.imglib2.img.cell.CellImg;
import spim.process.cache.LocalCachedImgCells.CellCache;

public class LocalCellCache implements CacheControl
{
	/**
	 * Key for a cell identified by timepoint, setup, level, and index
	 * (flattened spatial coordinate).
	 */
	protected final LoadingVolatileCache< Long, LocalCachedCell< ? > > volatileCache; // TODO rename

	public LocalCellCache()
	{
		volatileCache = new LoadingVolatileCache<>( 1, 0 );
	}

	/**
	 * Prepare the cache for providing data for the "next frame":
	 * <ul>
	 * <li>Move pending cell request to the prefetch queue (
	 * {@link BlockingFetchQueues#clearToPrefetch()}).
	 * <li>Perform pending cache maintenance operations (
	 * {@link WeakSoftCache#cleanUp()}).
	 * <li>Increment the internal frame counter, which will enable previously
	 * enqueued requests to be enqueued again for the new frame.
	 * </ul>
	 */
	@Override
	public void prepareNextFrame()
	{
		volatileCache.prepareNextFrame();
	}

	/**
	 * (Re-)initialize the IO time budget, that is, the time that can be spent
	 * in blocking IO per frame/
	 *
	 * @param partialBudget
	 *            Initial budget (in nanoseconds) for priority levels 0 through
	 *            <em>n</em>. The budget for level <em>i&gt;j</em> must always be
	 *            smaller-equal the budget for level <em>j</em>. If <em>n</em>
	 *            is smaller than the maximum number of mipmap levels, the
	 *            remaining priority levels are filled up with budget[n].
	 */
	@Override
	public void initIoTimeBudget( final long[] partialBudget )
	{
		volatileCache.initIoTimeBudget( partialBudget );
	}

	/**
	 * Get the {@link CacheIoTiming} that provides per thread-group IO
	 * statistics and budget.
	 */
	@Override
	public CacheIoTiming getCacheIoTiming()
	{
		return volatileCache.getCacheIoTiming();
	}

	/**
	 * Remove all references to loaded data as well as all enqueued requests
	 * from the cache.
	 */
	public void clearCache()
	{
		volatileCache.invalidateAll();
	}

	/**
	 * <em>For internal use.</em>
	 * <p>
	 * Get the {@link LoadingVolatileCache} that handles cell loading. This is
	 * used by bigdataviewer-server to directly issue Cell requests without
	 * having {@link CellImg}s and associated {@link CellCacheImpl}s.
	 *
	 * @return the cache that handles cell loading
	 */
	public LoadingVolatileCache< Long, LocalCachedCell< ? > > getLoadingVolatileCache()
	{
		return volatileCache;
	}

	/**
	 * A {@link VolatileCacheValueLoader} for one specific {@link LocalCachedCell}.
	 */
	public static class VolatileCellLoader< A > implements VolatileCacheValueLoader< LocalCachedCell< A > >
	{
		private final CacheArrayLoader< A > cacheArrayLoader;

		private final int[] cellDims;

		private final long[] cellMin;

		/**
		 * Create a loader for a specific cell.
		 *
		 * @param cacheArrayLoader
		 *            loads cell data
		 * @param cellDims
		 *            dimensions of the cell in pixels
		 * @param cellMin
		 *            minimum spatial coordinates of the cell in pixels
		 */
		public VolatileCellLoader(
				final CacheArrayLoader< A > cacheArrayLoader,
				final int[] cellDims,
				final long[] cellMin
				)
		{
			this.cacheArrayLoader = cacheArrayLoader;
			this.cellDims = cellDims;
			this.cellMin = cellMin;
		}

		@Override
		public LocalCachedCell< A > createEmptyValue()
		{
			return new LocalCachedCell<>( cellDims, cellMin, cacheArrayLoader.emptyArray( cellDims ) );
		}

		@Override
		public LocalCachedCell< A > load() throws InterruptedException
		{
			return new LocalCachedCell<>( cellDims, cellMin, cacheArrayLoader.loadArray( cellDims, cellMin ) );
		}
	}

	/**
	 * A {@link CellCache} that forwards to the {@link LocalCellCache}.
	 *
	 * @param <A>
	 *
	 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
	 */
	public class CellCacheImpl< A > implements CellCache< A >
	{
		private final CacheHints cacheHints;

		private final CacheArrayLoader< A > cacheArrayLoader;

		public CellCacheImpl( final CacheArrayLoader< A > cacheArrayLoader )
		{
			this.cacheHints = new CacheHints( LoadingStrategy.BLOCKING, 0, false );
			this.cacheArrayLoader = cacheArrayLoader;
		}

		@SuppressWarnings( "unchecked" )
		@Override
		public LocalCachedCell< A > get( final long index )
		{
			return ( LocalCachedCell< A > ) volatileCache.getIfPresent( index, cacheHints );
		}

		@SuppressWarnings( "unchecked" )
		@Override
		public LocalCachedCell< A > load( final long index, final int[] cellDims, final long[] cellMin )
		{
			final VolatileCellLoader< A > loader = new VolatileCellLoader<>( cacheArrayLoader, cellDims, cellMin );
			return ( LocalCachedCell< A > ) volatileCache.get( index, cacheHints, loader );
		}
	}
}
