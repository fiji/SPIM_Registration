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
package mpicbg.spim.vis3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import spim.vecmath.Transform3D;
import spim.vecmath.Point3d;
import spim.vecmath.Point3f;

import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.bead.Bead;
import mpicbg.spim.registration.segmentation.Nucleus;

public class VisualizationSketchTikZ
{
	public static String drawBeads( final Collection<Bead> beads, final Transform3D globalTransform, final String beadType, final double factor )
	{
		return drawBeads( beads, globalTransform, beadType, factor, 1 );
	}
	
	public static String drawBeads( final Collection<Bead> beads, final Transform3D globalTransform, final String beadType, final double factor, final int skipBeads )
	{				
		// we will insert lines like this
		// put { translate([0,0,1]) } {Bead}
		
		final String template1 = "\t\tput { translate([";
		final String template2 = "]) } {";
		final String template3 = "}\n";
		String insert = "";

		// the transformed bead position
		final Point3d translation = new Point3d();
		
		int beadCount = 0;
		
		// add all beads
		for ( Iterator<Bead> i = beads.iterator(); i.hasNext(); )
		{
			final Bead bead = i.next();
			
			// if it is a RANSAC bead draw it anyways, otherwise draw only every nth bead
			if ( bead.getRANSACCorrespondence().size() > 0 || beadCount % skipBeads == 0)
			{
				// set the bead coordinates
				translation.set( bead.getL() );
				
				// transform the bead coordinates into the position of the view
				globalTransform.transform( translation );
				
				insert += template1 + (translation.x*factor) + "," + (translation.y*factor) +"," + (translation.z*factor) + template2 + beadType + template3;
			}
			++beadCount;
		}				

		return insert;
	}

	public static String drawNuclei( final Collection<Nucleus> nuclei, final Transform3D globalTransform, final double factor )
	{				
		// we will insert lines like this
		// put { translate([0,0,1]) } {Nucleus}
		
		final String template1 = "\t\tput { translate([";
		final String template2 = "]) } {";
		final String template3 = "}\n";
		String insert = "";

		// the transformed bead position
		final Point3d translation = new Point3d();
		
		int j = 0;
		
		// add all beads
		for ( Iterator<Nucleus> i = nuclei.iterator(); i.hasNext(); )
		{
			final Nucleus nucleus = i.next();

			final String nucleusType;
			
			if ( nucleus.getRANSACCorrespondence().size() > 0 )
				nucleusType = "TrueNucleus";
			else if ( nucleus.getICPCorrespondence().size() > 0 )			
				nucleusType = "ICPNucleus";			
			else if ( nucleus.getDescriptorCorrespondence().size() > 0 && nucleus.getRANSACCorrespondence().size() == 0 )
				nucleusType = null;
			else
				nucleusType = "Nucleus";
			
			// set the bead coordinates
			translation.set( nucleus.getL() );
			
			// transform the bead coordinates into the position of the view
			globalTransform.transform( translation );
			
			if ( nucleusType != null )// && j++ % 10 == 0 )
				insert += template1 + (translation.x*factor) + "," + (translation.y*factor) +"," + (translation.z*factor) + template2 + nucleusType + template3;
		}				

		return insert;
	}

	public static String drawBead( final Bead bead, final Transform3D globalTransform, final String beadType, final double factor )
	{				
		// we will insert lines like this
		// put { translate([0,0,1]) } {Bead}
		
		final String template1 = "\t\tput { translate([";
		final String template2 = "]) } {";
		final String template3 = "}\n";
		String insert = "";

		// the transformed bead position
		final Point3d translation = new Point3d();
		
		// add beads	
		
		// set the bead coordinates
		translation.set( bead.getL() );
		
		// transform the bead coordinates into the position of the view
		globalTransform.transform( translation );
		
		insert += template1 + (translation.x*factor) + "," + (translation.y*factor) +"," + (translation.z*factor) + template2 + beadType + template3;

		return insert;
	}

	public static String drawNucleus( final Nucleus nucleus, final Transform3D globalTransform, final String beadType, final double factor )
	{			
		// we will insert lines like this
		// put { translate([0,0,1]) } {Bead}
		
		final String template1 = "\t\tput { translate([";
		final String template2 = "]) } {";
		final String template3 = "}\n";
		String insert = "";

		// the transformed bead position
		final Point3d translation = new Point3d();
		
		// add beads	
		
		// set the bead coordinates
		translation.set( nucleus.getL() );
		
		// transform the bead coordinates into the position of the view
		globalTransform.transform( translation );
		
		//insert += template1 + (translation.x*factor) + "," + (translation.y*factor) +"," + (translation.z*factor) + template2 + beadType + template3;
		//return insert;

		final StringBuffer s = new StringBuffer( template1 );
		
		s.append( translation.x*factor );
		s.append( "," ); 
		s.append( translation.y*factor );
		s.append( "," );
		s.append( translation.z*factor );
		s.append( template2 );
		s.append( beadType );
		s.append( template3 );
		
		return s.toString();
	}
	
	public static String drawView( final ViewDataBeads view, final double factor )
	{
		final ArrayList<Point3f> box = VisualizationFunctions.getTransformedBoundingBox( view );
		
		// we will insert lines like this
		// line[lineStyle](0,0,0)(10,0,10)
		
		final String template1 = "\t\tline[lineStyle](";
		final String template2 = ")(";
		final String template3 = ")\n";
		String insert = "";

		for ( int i = 0; i < box.size()/2; i++ )
		{
			Point3f from = box.get( i*2 );
			Point3f to = box.get( i*2 + 1 );
			
			insert += template1 + (from.x*factor) + "," + (from.y*factor) +"," + (from.z*factor) + template2 + (to.x*factor) + "," + (to.y*factor) +"," + (to.z*factor) + template3;
		}
		
		return insert;
	}
	
	public static String drawViews( final ViewDataBeads[] views, final float factor )
	{
		String insert = "";
		
		for (int i = 0; i < views.length; i++)
			insert += drawView( views[i], i );	
		
		return insert;
	}
	
}
