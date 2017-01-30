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
package mpicbg.spim.postprocessing.deconvolution2;

import java.util.ArrayList;

import mpicbg.spim.postprocessing.deconvolution2.LRFFT.PSFTYPE;

public class LRInput 
{
	public final static float minValue = 0.0001f;	
	final ArrayList< LRFFT > views = new ArrayList<LRFFT>();
	
	public void add( final LRFFT view )
	{
		views.add( view );
		
		for ( final LRFFT v : views )
			v.setNumViews( getNumViews() );
	}
		
	/**
	 * init all views
	 * 
	 *
	 * @return the same instance again for convinience
	 */
	public LRInput init( final PSFTYPE iterationType )
	{
		for ( final LRFFT view : views )
			view.init( iterationType, views );
		
		return this;
	}
	
	/**
	 * @return - the image data
	 */
	public ArrayList< LRFFT > getViews() { return views; }
	
	/**
	 * The number of views for this deconvolution
	 * @return
	 */
	public int getNumViews() { return views.size(); }
	
	@Override
	public LRInput clone()
	{
		LRInput clone = new LRInput();
		
		for ( final LRFFT view : views )
			clone.add( view.clone() );
		
		return clone;
	}
}
