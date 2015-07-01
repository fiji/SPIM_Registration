package spim.headless.interestpointdetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.wrapper.ImgLib2;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.process.interestpointdetection.Downsample;
import spim.process.interestpointdetection.ProcessDOM;

/**
 * Created by schmied on 01/07/15.
 */
public class DoM {

    final DoMParameters dom;

    public DoM( final DoMParameters dom ) { this.dom = dom; }

    public static HashMap<ViewId, List<InterestPoint>> findInterestPoints(final DoMParameters dom) {

        final HashMap<ViewId, List<InterestPoint>> interestPoints = new HashMap<ViewId, List<InterestPoint>>();

        //TODO: special iterator that takes into account missing views
        // make sure not everything crashes if one file is missing
        for (final ViewDescription vd : dom.toProcess) {
            // make sure not everything crashes if one file is missing
            try {
                //
                // open the corresponding image (if present at this timepoint)
                //
                if (!vd.isPresent())
                    continue;

                final AffineTransform3D correctCoordinates = new AffineTransform3D();
                final RandomAccessibleInterval<net.imglib2.type.numeric.real.FloatType> input =
                        DownsampleTools.openAndDownsample(dom.imgloader, vd, correctCoordinates, dom.downsampleXY, dom.downsampleZ);

                final Image<FloatType> img = ImgLib2.wrapFloatToImgLib1((Img<net.imglib2.type.numeric.real.FloatType>) input);


                // Compute DifferenceOfMean
                final ArrayList<InterestPoint> ips =
                        ProcessDOM.compute(
                                img,
                                (Img<net.imglib2.type.numeric.real.FloatType>) input,
                                (int) dom.radius1,
                                (int) dom.radius2,
                                (float) dom.threshold,
                                dom.localization,
                                Math.min(dom.imageSigmaX, (float) dom.radius1),
                                Math.min(dom.imageSigmaY, (float) dom.radius1),
                                Math.min(dom.imageSigmaZ, (float) dom.radius1),
                                dom.findMin,
                                dom.findMax,
                                dom.minIntensity,
                                dom.maxIntensity);

                img.close();

                Downsample.correctForDownsampling(ips, correctCoordinates, dom.downsampleXY, dom.downsampleZ);

                interestPoints.put(vd, ips);
            } catch (Exception e) {
                IOFunctions.println("An error occured (Difference of Mean): " + e);
                IOFunctions.println("Failed to segment angleId: " +
                        vd.getViewSetup().getAngle().getId() + " channelId: " +
                        vd.getViewSetup().getChannel().getId() + " illumId: " +
                        vd.getViewSetup().getIllumination().getId() + ". Continuing with next one.");
                e.printStackTrace();
            }
        }

        return interestPoints;
    }
}