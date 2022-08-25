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
package spim;

import java.io.File;
import java.net.URISyntaxException;

public class Version
{
	final static String notFound = "JAR version could not be read.";

	private Version() {}

	public static String getVersion()
	{ 
		try
		{
			String name = new File( Version.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath() ).getName().trim();
			
			if ( name.equals( "classes" ) || !name.endsWith( ".jar" ) )
			{
				return notFound;
			}
			else
			{
				// e.g. SPIM_Registration-2.0.0.jar
				final int start = name.indexOf( "-" ) + 1;
				final int end = name.length() - 4;
				
				if ( end <= start || start < 0 )
					return notFound;
				else
					return name.substring( start, end );
			}
		}
		catch ( final URISyntaxException e )
		{
			return notFound;
		}
	}
}
