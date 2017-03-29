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
package mpicbg.spim.postprocessing.deconvolution;

import mpicbg.imglib.algorithm.fft.FourierConvolution;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;

public class LucyRichardsonFFT 
{
	final Image<FloatType> image, kernel, weight;
	final FourierConvolution<FloatType, FloatType> fftConvolution;
	
	Image<FloatType> viewContribution = null;
	
	public LucyRichardsonFFT( final Image<FloatType> image, final Image<FloatType> weight, final Image<FloatType> kernel, final int cpusPerView )
	{
		this.image = image;
		this.kernel = kernel;
		this.weight = weight;
		
		fftConvolution = new FourierConvolution<FloatType, FloatType>( image, kernel );	
		fftConvolution.setNumThreads( Math.max( 1, cpusPerView ) );
	}

	public Image<FloatType> getImage() { return image; }
	public Image<FloatType> getWeight() { return weight; }
	public Image<FloatType> getKernel() { return kernel; }
	public Image<FloatType> getViewContribution() { return viewContribution; }
	
	public FourierConvolution<FloatType, FloatType> getFFTConvolution() { return fftConvolution; }
	
	public void setViewContribution( final Image<FloatType> viewContribution )
	{
		if ( this.viewContribution != null )
			this.viewContribution.close();
		
		this.viewContribution = viewContribution;
	}
}
