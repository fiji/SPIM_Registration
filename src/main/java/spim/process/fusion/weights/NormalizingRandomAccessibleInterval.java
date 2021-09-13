/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2021 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package spim.process.fusion.weights;

import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.type.numeric.RealType;

public class NormalizingRandomAccessibleInterval< T extends RealType< T > > implements RandomAccessibleInterval< T >
{
	final RandomAccessibleInterval< T > interval;
	final RandomAccessibleInterval< T > normalizeInterval;
	double osemspeedup;
	final T type;

	public NormalizingRandomAccessibleInterval(
			final RandomAccessibleInterval< T > interval,
			final RandomAccessibleInterval< T > normalizeInterval,
			final double osemspeedup,
			final T type )
	{
		// the assumption is that dimensionality & size matches, we do not test it tough
		this.interval = interval;
		this.normalizeInterval = normalizeInterval;
		this.osemspeedup = osemspeedup;
		this.type = type;
	}

	public NormalizingRandomAccessibleInterval(
			final RandomAccessibleInterval< T > interval,
			final RandomAccessibleInterval< T > normalizeInterval,
			final T type )
	{
		this( interval, normalizeInterval, 1, type );
	}

	public void setOSEMspeedup( final double osemspeedup ) { this.osemspeedup = osemspeedup; }
	public double getOSEMspeedup() { return osemspeedup; }

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new NormalizingRandomAccess< T >( interval, normalizeInterval, osemspeedup, type );
	}

	@Override
	public int numDimensions() { return interval.numDimensions(); }

	@Override
	public RandomAccess< T > randomAccess( final Interval interval ) { return randomAccess(); }

	@Override
	public long min( final int d ){ return interval.min( 0 ); }

	@Override
	public void min( final long[] min ) { interval.min( min ); }

	@Override
	public void min( final Positionable min ) { interval.min( min ); }

	@Override
	public long max( final int d ) { return interval.max( d ); }

	@Override
	public void max( final long[] max ) { interval.max( max ); }

	@Override
	public void max( final Positionable max ) { interval.max( max ); }

	@Override
	public double realMin( final int d ) { return interval.realMin( d ); }

	@Override
	public void realMin( final double[] min ) { interval.realMin( min ); }

	@Override
	public void realMin( final RealPositionable min ) { interval.realMin( min ); }

	@Override
	public double realMax( final int d ) { return interval.realMax( d ); }

	@Override
	public void realMax( final double[] max ) { interval.realMax( max ); }

	@Override
	public void realMax( final RealPositionable max ) { interval.realMax( max ); }

	@Override
	public void dimensions( final long[] dimensions ) { interval.dimensions( dimensions ); }

	@Override
	public long dimension( final int d ) { return interval.dimension( d ); }
}
