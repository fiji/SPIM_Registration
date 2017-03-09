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
	private SlideBook6Image[] illuminations;

	private int pixelType = -1;
	private int bytesPerPixel = -1; 
	private String pixelTypeString = "";
	private boolean isLittleEndian;
	private IFormatReader r = null;

	public int numIlluminations() { return illuminations.length; }

    public String imageName(final int i) { return illuminations[i].name; }
    public int numChannels(final int i) { return illuminations[i].channels.length; }
    public int numAngles(final int i) { return illuminations[i].angles.length; }
    public int numTimepoints(final int i) { return illuminations[i].numT; }
    public String objective(final int i) { return illuminations[i].objective; }
    public double calX(final int i) { return illuminations[i].calX; }
    public double calY(final int i) { return illuminations[i].calY; }
    public double calZ(final int i) { return illuminations[i].calZ; }
    public String[] channels(final int i) { return illuminations[i].channels; }
    public String[] angles(final int i) { return illuminations[i].angles; }
    public int[] imageSize(final int i) { return illuminations[i].imageSize;}

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
		final int numIlluminations = r.getSeriesCount();
		illuminations = new SlideBook6Image[numIlluminations];

		// only one debug ouput
		boolean printMetadata = false;

		Object tmp;

		try {
			for (int i = 0; i < illuminations.length; ++i) {
				r.setSeries(i);

				illuminations[i] = new SlideBook6Image();

                final MetadataRetrieve retrieve = (MetadataRetrieve) r.getMetadataStore();

                final int w = r.getSizeX();
				final int h = r.getSizeY();
				final int d = r.getSizeZ();
				illuminations[i].imageSize = new int[]{w, h, d};
				illuminations[i].numT = r.getSizeT();
                illuminations[i].name = retrieve.getImageName(i);

				try {
                    illuminations[i].objective = retrieve.getObjectiveSettingsID(i);
				} catch (Exception e) {
					IOFunctions.println("An error occured parsing the objective used: " + e + "\n. Proceeding.");
					illuminations[i].objective = "Unknown Objective";
					printMetadata = true;
				}

				try {
					illuminations[i].channels = new String[r.getSizeC()];

					for (int c = 0; c < r.getSizeC(); ++c) {
						illuminations[i].channels[c] = retrieve.getChannelName(i, c);
					}
				} catch (Exception e) {
					IOFunctions.println("An error occured parsing the channels: " + e + "\n. Proceeding.");
					for (int c = 0; c < r.getSizeC(); ++c)
						illuminations[i].channels[c] = String.valueOf(c);
					printMetadata = true;
				}

				try {
					String info = retrieve.getImageDescription(i);

					if (info.indexOf("stage") != -1) {
						illuminations[i].stageScan = true;
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
					illuminations[i].calX = cal;

					f = retrieve.getPixelsPhysicalSizeY(0);
					if (f != null)
						cal = f.value().floatValue();

					if (cal == 0) {
						cal = 1;
						IOFunctions.println("SlideBook6: Warning, calibration for dimension Y seems corrupted, setting to 1.");
					}
					illuminations[i].calY = cal;

					f = retrieve.getPixelsPhysicalSizeZ(0);
					if (f != null)
						cal = f.value().floatValue();

					if (cal == 0) {
						cal = 1;
						IOFunctions.println("SlideBook6: Warning, calibration for dimension Z seems corrupted, setting to 1.");
					}
					illuminations[i].calZ = cal;
				} catch (Exception e) {
					IOFunctions.println("An error occured parsing the calibration: " + e + "\n. Proceeding.");
					illuminations[i].calX = illuminations[i].calY = illuminations[i].calZ = 1;
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
