/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2023 Fiji developers.
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

import java.util.ArrayList;

import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.registration.ViewDataBeads;

public class BlendingFactory implements CombinedPixelWeightenerFactory<Blending>
{
	public Blending createInstance( ArrayList<ViewDataBeads> views ) { return new Blending( views ); }

	public void printProperties()
	{
		IOFunctions.println("BlendingFactory(): no special properties.");		
	}
	
	public String getDescriptiveName() { return "Blending"; }
	
	public String getErrorMessage() { return ""; }

	@Override
	public void setParameters(String configuration) { }
}
