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
package mpicbg.spim.fusion;

import ij.IJ;
import mpicbg.imglib.algorithm.fft.FourierConvolution;
import mpicbg.imglib.container.ContainerFactory;
import mpicbg.imglib.container.array.ArrayContainerFactory;
import mpicbg.imglib.cursor.Cursor;
import mpicbg.imglib.cursor.LocalizableByDimCursor;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.spim.io.SPIMConfiguration;
import mpicbg.spim.registration.ViewDataBeads;

public class GaussContent extends IsolatedPixelWeightener<GaussContent> 
{
	Image<FloatType> gaussContent;
	
	protected GaussContent( final ViewDataBeads view, final ContainerFactory entropyContainer ) 
	{
		super( view );
		
		try
		{			
			final SPIMConfiguration conf = view.getViewStructure().getSPIMConfiguration();
			
			// get the kernels			
			final double[] k1 = new double[ view.getNumDimensions() ];
			final double[] k2 = new double[ view.getNumDimensions() ];
			
			for ( int d = 0; d < view.getNumDimensions() - 1; ++d )
			{
				k1[ d ] = conf.fusionSigma1;
				k2[ d ] = conf.fusionSigma2;
			}
			
			k1[ view.getNumDimensions() - 1 ] = conf.fusionSigma1 / view.getZStretching();
			k2[ view.getNumDimensions() - 1 ] = conf.fusionSigma2 / view.getZStretching();		
			
			final Image<FloatType> kernel1 = FourierConvolution.createGaussianKernel( new ArrayContainerFactory(), k1 );
			final Image<FloatType> kernel2 = FourierConvolution.createGaussianKernel( new ArrayContainerFactory(), k2 );
	
			// compute I*sigma1
			FourierConvolution<FloatType, FloatType> fftConv1 = new FourierConvolution<FloatType, FloatType>( view.getImage(), kernel1 );
			
			fftConv1.process();		
			final Image<FloatType> conv1 = fftConv1.getResult();
			
			fftConv1.close();
			fftConv1 = null;
					
			// compute ( I - I*sigma1 )^2
			final Cursor<FloatType> cursorImg = view.getImage().createCursor();
			final Cursor<FloatType> cursorConv = conv1.createCursor();
			
			while ( cursorImg.hasNext() )
			{
				cursorImg.fwd();
				cursorConv.fwd();
				
				final float diff = cursorImg.getType().get() - cursorConv.getType().get();
				
				cursorConv.getType().set( diff*diff );
			}
	
			// compute ( ( I - I*sigma1 )^2 ) * sigma2
			FourierConvolution<FloatType, FloatType> fftConv2 = new FourierConvolution<FloatType, FloatType>( conv1, kernel2 );
			fftConv2.process();	
			
			gaussContent = fftConv2.getResult();

			fftConv2.close();
			fftConv2 = null;
			
			// close the unnecessary image
			kernel1.close();
			kernel2.close();
			conv1.close();
			
			ViewDataBeads.normalizeImage( gaussContent );
		}
		catch ( OutOfMemoryError e )
		{
			IJ.log( "OutOfMemory: Cannot compute Gauss approximated Entropy for " + view.getName() + ": " + e );
			e.printStackTrace();
			gaussContent = null;
		}
	}

	@Override
	public LocalizableByDimCursor<FloatType> getResultIterator()
	{
        // the iterator we need to get values from the weightening image
		return gaussContent.createLocalizableByDimCursor();
	}
	
	@Override
	public LocalizableByDimCursor<FloatType> getResultIterator( OutOfBoundsStrategyFactory<FloatType> factory )
	{
        // the iterator we need to get values from the weightening image
		return gaussContent.createLocalizableByDimCursor( factory );
	}
	
	@Override
	public void close()
	{
		gaussContent.close();
	}

	@Override
	public Image<FloatType> getResultImage() {
		return gaussContent;
	}
}
