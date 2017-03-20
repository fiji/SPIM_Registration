/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji developers.
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
package spim.process.fusion.deconvolution;

import java.util.concurrent.Callable;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.weights.Blending;

/**
 * Fuse one portion of a paralell fusion, supports no weights
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class TransformWeights implements Callable< String >
{
	final ImagePortion portion;
	final AffineTransform3D transform;
	final RandomAccessibleInterval< FloatType > blendingImg, overlapImg;
	final Blending blending;
	final int offsetX, offsetY, offsetZ, imgSizeX, imgSizeY, imgSizeZ;

	public TransformWeights(
			final ImagePortion portion,
			final Interval imgInterval,
			final Blending blending,
			final AffineTransform3D transform,
			final RandomAccessibleInterval< FloatType > overlapImg,
			final RandomAccessibleInterval< FloatType > blendingImg,
			final long[] offset )
	{
		this.portion = portion;
		this.blendingImg = blendingImg;
		this.transform = transform;
		this.overlapImg = overlapImg;
		this.blending = blending;

		this.offsetX = (int)offset[ 0 ];
		this.offsetY = (int)offset[ 1 ];
		this.offsetZ = (int)offset[ 2 ];

		this.imgSizeX = (int)imgInterval.dimension( 0 );
		this.imgSizeY = (int)imgInterval.dimension( 1 );
		this.imgSizeZ = (int)imgInterval.dimension( 2 );
	}

	@Override
	public String call() throws Exception 
	{
		// make the blending and get the transformations
		final RealRandomAccess< FloatType > wr = blending.realRandomAccess();

		final Cursor< FloatType > cursorO = Views.iterable( overlapImg ).localizingCursor();
		final Cursor< FloatType > cursorB = Views.iterable( blendingImg ).cursor();

		final float[] s = new float[ 3 ];
		final float[] t = new float[ 3 ];

		cursorO.jumpFwd( portion.getStartPosition() );
		cursorB.jumpFwd( portion.getStartPosition() );

		for ( int j = 0; j < portion.getLoopSize(); ++j )
		{
			// move img cursor forward any get the value (saves one access)
			final FloatType o = cursorO.next();
			cursorO.localize( s );
			
			// move weight cursor forward and get the value 
			final FloatType b = cursorB.next();

			s[ 0 ] += offsetX;
			s[ 1 ] += offsetY;
			s[ 2 ] += offsetZ;

			transform.applyInverse( t, s );

			// compute weights in any part of the image (the border can be negative!)
			wr.setPosition( t );

			o.set( o.get() + 1 );
			b.set( wr.get() );
		}

		return portion + " finished successfully (visualize weights).";
	}
}
