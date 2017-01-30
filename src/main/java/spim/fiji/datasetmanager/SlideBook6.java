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

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.SlideBook6ImgLoader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class SlideBook6 implements MultiViewDatasetDefinition {
    public static String[] rotAxes = new String[]{"X-Axis", "Y-Axis", "Z-Axis"};

    public static String defaultFilePath = "";
    public static boolean defaultModifyCal = false;
    public static int defaultCapture = -1;
    public static double defaultAngles[] = {0.0f, 90.0f};
    public static float defaultCalibrations[] = {1.0f, 1.0f, 1.0f};

    @Override
    public String getTitle() {
        return "Slidebook6 Dataset";
    }

    @Override
    public String getExtendedDescription() {
        return "This datset definition supports files saved by SlideBook6";
    }

    @Override
    public SpimData2 createDataset() {
        final File sldFile = querySLDFile();

        if (sldFile == null)
            return null;

        final SlideBook6MetaData meta = new SlideBook6MetaData();

        if (!meta.loadMetaData(sldFile)) {
            IOFunctions.println("Failed to analyze file.");
            return null;
        }

        if (!showDialogs(meta)) {
            return null;
        }

        final String directory = sldFile.getParent();
        final ImgFactory<? extends NativeType<?>> imgFactory = selectImgFactory(meta);

        // assemble timepoints, viewsetups, missingviews and the imgloader
        final TimePoints timepoints = this.createTimePoints(meta);
        final ArrayList<ViewSetup> setups = this.createViewSetups(meta);
        final MissingViews missingViews = null;

        // instantiate the sequencedescription
        final SequenceDescription sequenceDescription = new SequenceDescription(timepoints, setups, null, missingViews);
        final ImgLoader imgLoader = new SlideBook6ImgLoader(sldFile, imgFactory, sequenceDescription);
        sequenceDescription.setImgLoader(imgLoader);

        // get the minimal resolution of all calibrations, TODO: different views can have different calibrations?
        final float zSpacing = defaultCalibrations[2];
        final double minResolution = Math.min(defaultCalibrations[0], zSpacing);

        IOFunctions.println("Minimal resolution in all dimensions is: " + minResolution);
        IOFunctions.println("(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)");

        // create the initial view registrations (they are all the identity transform)
        final ViewRegistrations viewRegistrations = StackList.createViewRegistrations(sequenceDescription.getViewDescriptions(), minResolution);

        // create the initial view interest point object
        final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
        viewInterestPoints.createViewInterestPoints(sequenceDescription.getViewDescriptions());

        // finally create the SpimData itself based on the sequence description and the view registration
        final SpimData2 spimData = new SpimData2(new File(directory), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes());

        // Apply_Transformation.applyAxis( spimData );
        applyAxis(spimData, minResolution);

        return spimData;
    }

    public static void applyAxis(final SpimData data, final double minResolution) {
        ViewRegistrations viewRegistrations = data.getViewRegistrations();
        for (final ViewDescription vd : data.getSequenceDescription().getViewDescriptions().values()) {
            if (vd.isPresent()) {
                final Angle a = vd.getViewSetup().getAngle();

                if (a.hasRotation()) {
                    final ViewRegistration vr = viewRegistrations.getViewRegistration(vd);

                    final Dimensions dim = vd.getViewSetup().getSize();

                    VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSizeOrLoad(vd.getViewSetup(), vd.getTimePoint(), data.getSequenceDescription().getImgLoader());
                    final double calX = voxelSize.dimension(0) / minResolution;
                    final double calY = voxelSize.dimension(1) / minResolution;
                    final double calZ = voxelSize.dimension(2) / minResolution;

                    AffineTransform3D calModel = new AffineTransform3D();
                    calModel.set(
                            1, 0, 0, -dim.dimension(0) / 2 * calX,
                            0, 1, 0, -dim.dimension(1) / 2 * calY,
                            0, 0, 1, -dim.dimension(2) / 2 * calZ);
                    ViewTransform vt = new ViewTransformAffine("Center view", calModel);
                    vr.preconcatenateTransform(vt);

                    final double[] tmp = new double[16];
                    final double[] axis = a.getRotationAxis();
                    final double degrees = a.getRotationAngleDegrees();
                    final AffineTransform3D rotModel = new AffineTransform3D();
                    final String d;

                    if (axis[0] == 1 && axis[1] == 0 && axis[2] == 0) {
                        rotModel.rotate(0, Math.toRadians(degrees));
                        d = "Rotation around x-axis by " + degrees + " degrees";
                    } else if (axis[0] == 0 && axis[1] == 1 && axis[2] == 0) {
                        rotModel.rotate(1, Math.toRadians(degrees));
                        d = "Rotation around y-axis by " + degrees + " degrees";
                    } else if (axis[0] == 0 && axis[0] == 0 && axis[2] == 1) {
                        rotModel.rotate(2, Math.toRadians(degrees));
                        d = "Rotation around z-axis by " + degrees + " degrees";
                    } else {
                        IOFunctions.println("Arbitrary rotation axis not supported yet.");
                        continue;
                    }

                    vt = new ViewTransformAffine(d, rotModel);
                    vr.preconcatenateTransform(vt);
                    vr.updateModel();
                }
            }
        }
    }

    /**
     * Creates the List of {@link ViewSetup} for the {@link SpimData} object.
     * The {@link ViewSetup} are defined independent of the {@link TimePoint},
     * each {@link TimePoint} should have the same {@link ViewSetup}s. The {@link MissingViews}
     * class defines if some of them are missing for some of the {@link TimePoint}s
     *
     * @return
     */
    protected ArrayList<ViewSetup> createViewSetups(final SlideBook6MetaData meta) {
        // TODO: query rotation angle of each SlideBook channel
        final double[] yaxis = new double[]{0, 1, 0};
        final ArrayList<Angle> angles = new ArrayList<Angle>();
        final Angle angleA = new Angle(0, "Path_A");
        angleA.setRotation(yaxis, defaultAngles[0]);
        angles.add(angleA);

        final Angle angleB = new Angle(1, "Path_B");
        angleB.setRotation(yaxis, defaultAngles[1]);
        angles.add(angleB);

        // define multiple illuminations, one for every capture in the slide file
        final ArrayList<ViewSetup> viewSetups = new ArrayList<ViewSetup>();

        int firstCapture = defaultCapture;
        int numCaptures = 1;
        if (defaultCapture == -1) {
            firstCapture = 0;
            numCaptures = meta.numCaptures();
        }

        // create one illumination for each capture if defaultCapture == -1, otherwise just one capture
        for (int capture = firstCapture; capture < firstCapture + numCaptures; capture++) {
            final int channels = meta.numChannels(capture);

            // multi-angle captures must have pairs of channels
            if (channels > 1 && channels % 2 == 0) {
                String imageName = meta.imageName(capture);
                imageName = imageName.replaceAll("[^a-zA-Z0-9_-]", "_"); // convert illegal characters to _
                imageName = imageName.toLowerCase(); // convert to lowercase
                // up to 8 illuminations (SlideBook channels) per SlideBook capture
                final Illumination i = new Illumination(capture * 8, imageName);

                for (int ch = 0; ch < channels / 2; ch++) {
                    // use name of first channel, SlideBook channels are diSPIM angles and each SlideBook image should have two angles per channel
                    String channelName = meta.channels(capture)[ch * 2];
                    channelName = channelName.replaceAll("[^a-zA-Z0-9_-]", "_"); // convert illegal characters to _
                    channelName = channelName.toLowerCase(); // convert to lowercase

                    final Channel channel = new Channel(ch, channelName);

                    for (final Angle a : angles) {
                        float voxelSizeUm = defaultCalibrations[0];
                        float zSpacing = defaultCalibrations[2];

                        final VoxelDimensions voxelSize = new FinalVoxelDimensions("um", voxelSizeUm, voxelSizeUm, zSpacing);
                        final Dimensions dim = new FinalDimensions(new long[]{meta.imageSize(capture)[0], meta.imageSize(capture)[1], meta.imageSize(capture)[2]});

                        viewSetups.add(new ViewSetup(viewSetups.size(), a.getName(), dim, voxelSize, channel, a, i));
                    }
                }
            }
        }

        return viewSetups;
    }

    /**
     * Creates the {@link TimePoints} for the {@link SpimData} object
     */
    protected TimePoints createTimePoints(final SlideBook6MetaData meta) {
        final ArrayList<TimePoint> timepoints = new ArrayList<TimePoint>();

        int firstCapture = defaultCapture;
        int numCaptures = 1;
        if (defaultCapture == -1) {
            firstCapture = 0;
            numCaptures = meta.numCaptures();
        }

        int t = 0;
        for (; t < meta.numTimepoints(firstCapture); ++t)
            timepoints.add(new TimePoint(t));

        return new TimePoints(timepoints);
    }

    protected ImgFactory<? extends NativeType<?>> selectImgFactory(final SlideBook6MetaData meta) {
        int[] dims = meta.imageSize(0);
        long maxNumPixels = dims[0];
        maxNumPixels *= dims[1];
        maxNumPixels *= dims[2];

        String s = "Maximum number of pixels in any view: n=" + Long.toString(maxNumPixels) +
                " px ";

        if (maxNumPixels < Integer.MAX_VALUE) {
            IOFunctions.println(s + "< " + Integer.MAX_VALUE + ", using ArrayImg.");
            return new ArrayImgFactory<FloatType>();
        } else {
            IOFunctions.println(s + ">= " + Integer.MAX_VALUE + ", using CellImg.");
            return new CellImgFactory<FloatType>(256);
        }
    }

    protected boolean showDialogs(final SlideBook6MetaData meta) {
        {
            GenericDialog gd = new GenericDialog("Select SlideBook6 diSPIM Capture");

            String[] imageNames = new String[meta.numCaptures() + 1];
            imageNames[0] = new String("< All Captures >");
            for (int c = 0; c < meta.numCaptures(); c++) {
                imageNames[c + 1] = meta.imageName(c);
            }

            // pick which image capture to process
            gd.addChoice("Capture Name", imageNames, imageNames[0]);

            GUIHelper.addScrollBars(gd);

            gd.showDialog();

            if (gd.wasCanceled())
                return false;

            defaultCapture = gd.getNextChoiceIndex() - 1;
        }

        int firstCapture = defaultCapture;
        if (defaultCapture == -1) {
            firstCapture = 0;
        }

        // check for invalid image ( single channel == single angle )
        int numChannels = meta.numChannels(firstCapture);
        if (numChannels < 2 || numChannels % 2 != 0) {
            IOFunctions.println("ERROR: " + numChannels + " angles detected.  No alignment possible.");
            return false;
        }

        {
            GenericDialog gd = new GenericDialog("SlideBook6 diSPIM Properties");

            gd.addMessage("Angles (" + numChannels + " present)", new Font(Font.SANS_SERIF, Font.BOLD, 13));
            gd.addMessage("");

            for (int ch = 0; ch < numChannels; ch++) {
                gd.addNumericField("Angle of '" + meta.channels(firstCapture)[ch] + "':", (ch % 2) * 90, 1, 5, "degrees");
            }

            gd.addMessage("Channels (" + meta.numChannels(firstCapture) / 2 + " present)", new Font(Font.SANS_SERIF, Font.BOLD, 13));
            gd.addMessage("");

            for (int ch = 0; ch < meta.numChannels(firstCapture) / 2; ++ch) {
                gd.addMessage("Channel_" + (ch + 1) + ": " + meta.channels(firstCapture)[ch* 2]);
            }

            gd.addMessage("Timepoints (" + meta.numTimepoints(firstCapture) + " present)", new Font(Font.SANS_SERIF, Font.BOLD, 13));

            defaultCalibrations[0] = (float) meta.calX(firstCapture);
            defaultCalibrations[1] = (float) meta.calY(firstCapture);
            defaultCalibrations[2] = (float) meta.calZ(firstCapture);

            gd.addMessage("Calibration", new Font(Font.SANS_SERIF, Font.BOLD, 13));
            gd.addCheckbox("Modify_calibration", defaultModifyCal);
            gd.addMessage(
                    "Pixel Distance X: " + defaultCalibrations[0] + " " + "um" + "\n" +
                            "Pixel Distance Y: " + defaultCalibrations[1] + " " + "um" + "\n" +
                            "Pixel Distance Z: " + defaultCalibrations[2] + " " + "um" + "\n");

            gd.addMessage(
                    "Rotation axis: " + "Y" + " axis\n" +
                            "Pixel type: " + "UInt16",
                    new Font(Font.SANS_SERIF, Font.ITALIC, 11));

            gd.showDialog();

            if (gd.wasCanceled())
                return false;

            // use the user specified angles
            for (int a = 0; a < meta.numChannels(firstCapture); ++a) {
                defaultAngles[a % 2] = gd.getNextNumber();
            }

            defaultModifyCal = gd.getNextBoolean();
        }

        if (defaultModifyCal) {
            GenericDialog gd = new GenericDialog("Modify Meta Data");

            gd.addNumericField("Pixel_distance_x", defaultCalibrations[0], 5, 5, "um");
            gd.addNumericField("Pixel_distance_y", defaultCalibrations[1], 5, 5, "um");
            gd.addNumericField("Pixel_distance_z", defaultCalibrations[2], 5, 5, "um");

            gd.showDialog();

            if (gd.wasCanceled())
                return false;

            defaultCalibrations[0] = (float) gd.getNextNumber();
            defaultCalibrations[1] = (float) gd.getNextNumber();
            defaultCalibrations[2] = (float) gd.getNextNumber();
        }

        return true;
    }

    protected File querySLDFile() {
        GenericDialogPlus gd = new GenericDialogPlus("Define SlideBook6 diSPIM Dataset");

        gd.addFileField("SlideBook6 SLD file", defaultFilePath, 50);
        gd.showDialog();

        if (gd.wasCanceled())
            return null;

        final File firstFile = new File(defaultFilePath = gd.getNextString());
        if (!firstFile.exists()) {
            IOFunctions.println("File '" + firstFile.getAbsolutePath() + "' does not exist. Stopping");
            return null;
        } else {
            IOFunctions.println("Investigating file '" + firstFile.getAbsolutePath() + "'.");
            return firstFile;
        }
    }

    @Override
    public SlideBook6 newInstance() {
        return new SlideBook6();
    }

    public static void main(String[] args) {
        //defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/worm7/Track1.czi";
        new SlideBook6().createDataset();
    }
}

