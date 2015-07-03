package spim.process.histogram;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import spim.fiji.plugin.thinout.ChannelProcessThinOut;
import spim.fiji.plugin.thinout.Histogram;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Created by Stephan Janosch on 03/07/15.
 */
public class HistogramDisplay {
    /**
     * Plot histogram.
     *
     * @param spimData the spim data
     * @param viewIds  the view ids
     * @param channel  the channel
     * @return the histogram
     */
    @Deprecated
    public static Histogram plotHistogram(final SpimData2 spimData, final List<ViewId> viewIds, final ChannelProcessThinOut channel) {
        final ViewInterestPoints vip = spimData.getViewInterestPoints();

        // list of all distances
        final ArrayList<Double> distances = new ArrayList<Double>();
        String unit = null;

        for (final ViewId viewId : viewIds) {
            final int subSampling = channel.getSubsampling();
            final ViewDescription vd = spimData.getSequenceDescription().getViewDescription(viewId);
            final String label = channel.getLabel();

            if (!vd.isPresent() || vd.getViewSetup().getChannel().getId() != channel.getChannel().getId())
                continue;

            unit = populateDistanceListAndGetUnitString(vip, distances, unit, viewId, subSampling, vd, label);
        }

        final Histogram h = new Histogram(distances, 100, "Distance Histogram [Channel=" + channel.getChannel().getName() + "]", unit);
        h.showHistogram();
        IOFunctions.println("Channel " + channel.getChannel().getName() + ": min distance=" + h.getMin() + ", max distance=" + h.getMax());
        return h;
    }

    /**
     * Populate distance list and get unit string.
     *
     * @param vip         the vip
     * @param distances   the distances
     * @param unit        the unit
     * @param viewId      the view id
     * @param subSampling the sub sampling
     * @param vd          the vd
     * @param label       the label
     * @return the string
     */
    public static String populateDistanceListAndGetUnitString(final ViewInterestPoints vip, ArrayList<Double> distances, String unit, final ViewId viewId, final int subSampling, final ViewDescription vd, final String label)
    {
        if (unit == null)
            unit = vd.getViewSetup().getVoxelSize().unit();

        final ViewInterestPointLists vipl = vip.getViewInterestPointLists(viewId);
        final InterestPointList ipl = vipl.getInterestPointList(label);

        final VoxelDimensions voxelSize = vd.getViewSetup().getVoxelSize();
        distances.addAll(getDistances(ipl, voxelSize, subSampling));
        return unit;
    }

    /**
     * Plot histogram.
     *
     * @param spimData    the spim data
     * @param viewIds     the view ids
     * @param label       the label
     * @param subSampling the sub sampling
     * @return the histogram
     */
    public static Histogram plotHistogram(final SpimData2 spimData, final Collection<ViewDescription> viewIds, final String label, final int subSampling)
    {
        final ViewInterestPoints vip = spimData.getViewInterestPoints();
        final ArrayList<Double> distances = new ArrayList<Double>();
        String unit = null;

        for (final ViewId viewId : viewIds) {
            final ViewDescription vd = spimData.getSequenceDescription().getViewDescription(viewId);

            unit = populateDistanceListAndGetUnitString(vip, distances, unit, viewId, subSampling, vd, label);

        }
        final Histogram h = new Histogram(distances, 100, "Distance Histogram ", unit);
        h.showHistogram();
        IOFunctions.println("min distance=" + h.getMin() + ", max distance=" + h.getMax());
        return h;
    }


    /**
     * method which calculates the distances see {@link #getDistances(java.util.List, mpicbg.spim.data.sequence.VoxelDimensions, double)}
     *
     * @param ipl         interestPointList
     * @param voxelSize   voxelDimensions
     * @param subSampling subsampling
     * @return list of differences
     */
    public static ArrayList<Double> getDistances(InterestPointList ipl, final VoxelDimensions voxelSize, final double subSampling)
    {

        if (ipl.getInterestPoints() == null)
            ipl.loadInterestPoints();

        List<InterestPoint> ipList = ipl.getInterestPoints();
        return getDistances(ipList, voxelSize, subSampling);
    }

    /**
     * method which calculates the distances
     *
     * @param ipList      the ip list
     * @param voxelSize   the voxel size
     * @param subSampling the sub sampling
     * @return the distances
     */
    public static ArrayList<Double> getDistances(List<InterestPoint> ipList, final VoxelDimensions voxelSize, final double subSampling)
    {
        // assemble the list of points
        final List<RealPoint> list = new ArrayList<RealPoint>();
        ArrayList<Double> viewDistanceList = new ArrayList<Double>();

        for (final InterestPoint ip : ipList) {
            list.add(new RealPoint(
                    ip.getL()[0] * voxelSize.dimension(0),
                    ip.getL()[1] * voxelSize.dimension(1),
                    ip.getL()[2] * voxelSize.dimension(2)));
        }

        // make the KDTree
        final KDTree<RealPoint> tree = new KDTree<RealPoint>(list, list);

        // Nearest neighbor for each point
        final KNearestNeighborSearchOnKDTree<RealPoint> nn = new KNearestNeighborSearchOnKDTree<RealPoint>(tree, 2);
        final Random rnd = new Random(System.currentTimeMillis());

        for (final RealPoint p : list) {
            // every n'th point only
            if (rnd.nextDouble() < 1.0 / subSampling) {
                nn.search(p);

                // first nearest neighbor is the point itself, we need the second nearest
                viewDistanceList.add(nn.getDistance(1));
            }
        }

        return viewDistanceList;
    }


}
