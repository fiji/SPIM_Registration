/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2022 Fiji developers.
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

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.Type;
import spim.Threads;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;

/**
 * Mirrors an n-dimensional image along an axis (one of the dimensions).
 * The calculation is performed in-place and multithreaded.
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class Mirror
{
	/**
	 * @param image - The {@link Img} to mirror
	 * @param dimension - The axis to mirror (e.g. 0-&gt;x-Axis-&gt;horizontally, 1-&gt;y-axis-&gt;vertically)
	 * @param numThreads - number of threads
	 */
	public static < T extends Type< T > > boolean mirror( final Img< T > image, final int dimension, final int numThreads )
	{
		final int n = image.numDimensions();

		// divide the image into chunks
		final long imageSize = image.size();
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( imageSize, numThreads * 4 );

		final long maxMirror = image.dimension( dimension ) - 1;
		final long sizeMirrorH = image.dimension( dimension ) / 2;

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

		for ( final ImagePortion portion : portions )
		{
			tasks.add( new Callable< Void >() 
			{
				@Override
				public Void call() throws Exception
				{
					final Cursor< T > cursorIn = image.localizingCursor();
					final RandomAccess< T > cursorOut = image.randomAccess();
					final T temp = image.firstElement().createVariable();
					final long[] position = new long[ n ];

					// set the cursorIn to right offset
					final long startPosition = portion.getStartPosition();
					final long loopSize = portion.getLoopSize();

					if ( startPosition > 0 )
						cursorIn.jumpFwd( startPosition );

					// iterate over all pixels, if they are above the middle switch them with their counterpart
					// from the other half in the respective dimension
					for ( long i = 0; i < loopSize; ++i )
					{
						cursorIn.fwd();
						cursorIn.localize( position );

						if ( position[ dimension ] <= sizeMirrorH )
						{
							// set the localizable to the correct mirroring position
							position[ dimension ] = maxMirror - position[ dimension ];
							cursorOut.setPosition( position );

							// do a triangle switching
							final T in = cursorIn.get();
							final T out = cursorOut.get();

							temp.set( in );
							in.set( out );
							out.set( temp );
						}
					}

					return null;
				}
			});
		}

		try
		{
			// invokeAll() returns when all tasks are complete
			taskExecutor.invokeAll( tasks );
		}
		catch ( final InterruptedException e )
		{
			IOFunctions.println( "Failed to compute downsampling: " + e );
			e.printStackTrace();
			return false;
		}

		taskExecutor.shutdown();

		return true;
	}
}
