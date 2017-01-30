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
package mpicbg.spim.vis3d;

import java.util.ArrayList;

import mpicbg.imglib.image.display.imagej.InverseTransformDescription;
import mpicbg.imglib.interpolation.InterpolatorFactory;
import mpicbg.imglib.interpolation.nearestneighbor.NearestNeighborInterpolatorFactory;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyValueFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.AbstractAffineModel3D;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.ViewStructure;

public class PreviewRegistration 
{
	public PreviewRegistration( final ViewStructure viewStructure )
	{
		final OutOfBoundsStrategyValueFactory<FloatType> outsideFactory = new OutOfBoundsStrategyValueFactory<FloatType>( new FloatType(0) );
		final InterpolatorFactory<FloatType> interpolatorFactory = new NearestNeighborInterpolatorFactory<FloatType>( outsideFactory );

		final ArrayList<InverseTransformDescription<FloatType>> list = new ArrayList<InverseTransformDescription<FloatType>>();
		
		for ( final ViewDataBeads view : viewStructure.getViews() )
		{
			if ( view.isConnected() )
			{
				InverseTransformDescription<FloatType> i = new InverseTransformDescription<FloatType>( (AbstractAffineModel3D<?>)view.getTile().getModel(), interpolatorFactory, view.getImage() );
				list.add( i );
			}
		}

		/*
		if ( list.size() > 0 )
			ImageJFunctions.displayAsVirtualStack( list, ImageJFunctions.GRAY32, new int[]{0,1,2}, new int[3] ).show();
		else
			if ( viewStructure.getDebugLevel() <= ViewStructure.DEBUG_ERRORONLY )*/
				IOFunctions.println("PreviewRegistration(): no view is connected to any other view, cannot display.");
	}
}
