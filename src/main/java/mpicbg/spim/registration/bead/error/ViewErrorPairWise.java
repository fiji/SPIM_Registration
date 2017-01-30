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
package mpicbg.spim.registration.bead.error;

import mpicbg.spim.registration.ViewDataBeads;

public class ViewErrorPairWise
{
	final ViewDataBeads view;
	protected boolean isConnected;
	protected double avgError;

	public ViewErrorPairWise( final ViewDataBeads view )
	{
		this.view = view;
		
		setConnected( false );
		setAvgError( -1 );
	}
	
	public ViewDataBeads getView() { return view; }
	
	public void setConnected( final boolean status ) { this.isConnected = status; }
	public void setAvgError( final double error ) { this.avgError = error; }
	
	public double getAvgError () { return avgError; }
	public boolean isConnected () { return isConnected; }
}
