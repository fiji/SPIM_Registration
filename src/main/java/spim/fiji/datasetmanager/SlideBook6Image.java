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
package spim.fiji.datasetmanager;

/**
 * Created by Richard on 2/15/2017.
 */
public class SlideBook6Image {
    // SlideBook image capture information
    public String name;
    public String objective = "";
    public String calUnit = "um";
    public String[] channels;
    public String[] angles = {"Path_A", "Path_B"};
    public int numT = -1;
    public double calX, calY, calZ = -1;
    public int[] imageSize = {-1, -1, -1};
    public boolean stageScan = false;

    public int numChannels() { return channels.length; }
    public int numAngles() { return angles.length; }
    public int numTimepoints() { return numT; }
}
