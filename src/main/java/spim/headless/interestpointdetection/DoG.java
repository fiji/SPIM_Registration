package spim.headless.interestpointdetection;

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
import spim.process.interestpointdetection.ProcessDOG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DoG
{
    final DoGParameters dog;

    public DoG(final DoGParameters dog) {
        this.dog = dog;
    }

    public static HashMap<ViewId, List<InterestPoint>> findInterestPoints(final DoGParameters dog)
    {
        final HashMap<ViewId, List<InterestPoint>> interestPoints = new HashMap<ViewId, List<InterestPoint>>();

        //TODO: special iterator that takes into account missing views
        for (final ViewDescription vd : dog.toProcess) {
            // make sure not everything crashes if one file is missing
            try {
                //
                // open the corresponding image (if present at this timepoint)
                //
                if (!vd.isPresent())
                    continue;

                final AffineTransform3D correctCoordinates = new AffineTransform3D();
                final RandomAccessibleInterval<net.imglib2.type.numeric.real.FloatType> input =
                        DownsampleTools.openAndDownsample(dog.imgloader, vd, correctCoordinates, dog.downsampleXY, dog.downsampleZ);

                final Image<FloatType> img = ImgLib2.wrapFloatToImgLib1((Img<net.imglib2.type.numeric.real.FloatType>) input);

                //
                // compute Difference-of-Mean
                //
                final ArrayList<InterestPoint> ips =
                        ProcessDOG.compute(
                                dog.cuda,
                                dog.deviceList,
                                dog.accurateCUDA,
                                dog.percentGPUMem,
                                img,
                                (Img<net.imglib2.type.numeric.real.FloatType>) input,
                                (float) dog.sigma,
                                (float) dog.threshold,
                                dog.localization,
                                Math.min(dog.imageSigmaX, (float) dog.sigma),
                                Math.min(dog.imageSigmaY, (float) dog.sigma),
                                Math.min(dog.imageSigmaZ, (float) dog.sigma),
                                dog.findMin,
                                dog.findMax,
                                dog.minIntensity,
                                dog.maxIntensity);

                img.close();

                Downsample.correctForDownsampling(ips, correctCoordinates, dog.downsampleXY, dog.downsampleZ);

                interestPoints.put(vd, ips);
            }
            catch (Exception e)
            {
                IOFunctions.println("An error occured (DOG): " + e);
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
