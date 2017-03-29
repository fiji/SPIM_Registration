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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;

import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.meta.MetadataRetrieve;
import mpicbg.spim.io.IOFunctions;
import ome.units.quantity.Length;
import spim.fiji.spimdata.imgloaders.LegacySlideBook6ImgLoader;

public class SlideBook6MetaData
{
	private SlideBook6Image[] captures;

	private int pixelType = -1;
	private int bytesPerPixel = -1; 
	private String pixelTypeString = "";
	private boolean isLittleEndian;
	private IFormatReader r = null;

	public int numCaptures() { return captures.length; }

    public String imageName(final int i) { return captures[i].name; }
    public int numChannels(final int i) { return captures[i].channels.length; }
    public int numAngles(final int i) { return captures[i].angles.length; }
    public int numTimepoints(final int i) { return captures[i].numT; }
    public String objective(final int i) { return captures[i].objective; }
    public double calX(final int i) { return captures[i].calX; }
    public double calY(final int i) { return captures[i].calY; }
    public double calZ(final int i) { return captures[i].calZ; }
    public String[] channels(final int i) { return captures[i].channels; }
    public String[] angles(final int i) { return captures[i].angles; }
    public int[] imageSize(final int i) { return captures[i].imageSize;}

	public int pixelType() { return pixelType; }
	public int bytesPerPixel() { return bytesPerPixel; }
	public String pixelTypeString() { return pixelTypeString; }
	public boolean isLittleEndian() { return isLittleEndian; }
	public IFormatReader getReader() { return r; }

	public boolean loadMetaData( final File sldFile )
	{
		return loadMetaData( sldFile, false );
	}

	public boolean loadMetaData(final File sldFile, final boolean keepFileOpen) {
		final IFormatReader r = LegacySlideBook6ImgLoader.instantiateImageReader();

		if (!LegacySlideBook6ImgLoader.createOMEXMLMetadata(r)) {
			try {
				r.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			IOFunctions.println("Creating MetaDataStore failed. Stopping");
			return false;
		}

		try {
			r.setId(sldFile.getAbsolutePath());

			this.pixelType = r.getPixelType();
			this.bytesPerPixel = FormatTools.getBytesPerPixel(pixelType);
			this.pixelTypeString = FormatTools.getPixelTypeString(pixelType);
			this.isLittleEndian = r.isLittleEndian();

			if (!(pixelType == FormatTools.UINT16)) {
				IOFunctions.println(
						"SlideBook6MetaData.loadMetaData(): PixelType " + pixelTypeString +
								" not supported yet. Please send me an email about this: stephan.preibisch@gmx.de - stopping.");

				r.close();

				return false;
			}

			//printMetaData( r );
		} catch (Exception e) {
			IOFunctions.println("File '" + sldFile.getAbsolutePath() + "' could not be opened: " + e);
			IOFunctions.println("Stopping");

			e.printStackTrace();
			try {
				r.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return false;
		}

		final Hashtable<String, Object> metaData = r.getGlobalMetadata();
		final int numCaptures = r.getSeriesCount();
		captures = new SlideBook6Image[numCaptures];

		// only one debug ouput
		boolean printMetadata = false;

		Object tmp;

		try {
			for (int i = 0; i < captures.length; ++i) {
				r.setSeries(i);

				captures[i] = new SlideBook6Image();

                final MetadataRetrieve retrieve = (MetadataRetrieve) r.getMetadataStore();

                final int w = r.getSizeX();
				final int h = r.getSizeY();
				final int d = r.getSizeZ();
				captures[i].imageSize = new int[]{w, h, d};
				captures[i].numT = r.getSizeT();
				captures[i].name = retrieve.getImageName(i);

				try {
					captures[i].objective = retrieve.getObjectiveSettingsID(i);
				} catch (Exception e) {
					IOFunctions.println("An error occured parsing the objective used: " + e + "\n. Proceeding.");
					captures[i].objective = "Unknown Objective";
					printMetadata = true;
				}

				try {
					captures[i].channels = new String[r.getSizeC()];

					for (int c = 0; c < r.getSizeC(); ++c) {
						captures[i].channels[c] = retrieve.getChannelName(i, c);
					}
				} catch (Exception e) {
					IOFunctions.println("An error occured parsing the channels: " + e + "\n. Proceeding.");
					for (int c = 0; c < r.getSizeC(); ++c)
						captures[i].channels[c] = String.valueOf(c);
					printMetadata = true;
				}

				try {
					String info = retrieve.getImageDescription(i);

					if (info.indexOf("stage") != -1) {
						captures[i].stageScan = true;
					}

				} catch (Exception e) {
					IOFunctions.println("An error occured parsing the info text for 'stage': " + e + "\n. Proceeding.");
					printMetadata = true;
				}

				try {
					float cal = 0;



                    Length f = retrieve.getPixelsPhysicalSizeX(0);
					if (f != null)
						cal = f.value().floatValue();

					if (cal == 0) {
						cal = 1;
						IOFunctions.println("SlideBook6: Warning, calibration for dimension X seems corrupted, setting to 1.");
					}
					captures[i].calX = cal;

					f = retrieve.getPixelsPhysicalSizeY(0);
					if (f != null)
						cal = f.value().floatValue();

					if (cal == 0) {
						cal = 1;
						IOFunctions.println("SlideBook6: Warning, calibration for dimension Y seems corrupted, setting to 1.");
					}
					captures[i].calY = cal;

					f = retrieve.getPixelsPhysicalSizeZ(0);
					if (f != null)
						cal = f.value().floatValue();

					if (cal == 0) {
						cal = 1;
						IOFunctions.println("SlideBook6: Warning, calibration for dimension Z seems corrupted, setting to 1.");
					}
					captures[i].calZ = cal;
				} catch (Exception e) {
					IOFunctions.println("An error occured parsing the calibration: " + e + "\n. Proceeding.");
					captures[i].calX = captures[i].calY = captures[i].calZ = 1;
					printMetadata = true;
				}
			}
		} catch (Exception e) {
			IOFunctions.println("An error occured parsing the main meta data: " + e + ". Stopping.");
			e.printStackTrace();
			printMetaData(r);
			try {
				r.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return false;
		}

		if (printMetadata)
			printMetaData(r);

		if (!keepFileOpen)
			try {
				r.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		else
			this.r = r;

		return true;
	}

	private static void printMetaData( final IFormatReader r )
	{
		printMetaData( r.getGlobalMetadata() );
	}

	private static void printMetaData( final Hashtable< String, Object > metaData )
	{
		ArrayList< String > entries = new ArrayList<>();

		for ( final String s : metaData.keySet() )
			entries.add( "'" + s + "': " + metaData.get( s ) );

		Collections.sort( entries );

		for ( final String s : entries )
			System.out.println( s );
	}
}
