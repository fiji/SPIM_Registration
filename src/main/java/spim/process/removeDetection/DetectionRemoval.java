package spim.process.removeDetection;

import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import spim.fiji.plugin.thinout.ChannelProcessThinOut;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.headless.removeDetection.RemoveDetectionParameters;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Stephan Janosch on 01/07/15.
 */
public class DetectionRemoval {

    /**
     * Gets filtered interest points. Used in @removeDetections
     *
     * @param minDistance the min distance
     * @param maxDistance the max distance
     * @param keepRange the keep range
     * @param oldIpl the old ipl
     * @param voxelSize the voxel size
     * @return the filtered interest points
     */
    private static ArrayList<InterestPoint> getFilteredInterestPoints(double minDistance, double maxDistance, boolean keepRange, InterestPointList oldIpl, VoxelDimensions voxelSize) {
        // assemble the list of points (we need two lists as the KDTree sorts the list)
        // we assume that the order of list2 and points is preserved!
        final List<RealPoint> list1 = new ArrayList<RealPoint>();
        final List<RealPoint> list2 = new ArrayList<RealPoint>();
        final List<double[]> points = new ArrayList<double[]>();

        for (final InterestPoint ip : oldIpl.getInterestPoints()) {
            list1.add(new RealPoint(
                    ip.getL()[0] * voxelSize.dimension(0),
                    ip.getL()[1] * voxelSize.dimension(1),
                    ip.getL()[2] * voxelSize.dimension(2)));

            list2.add(new RealPoint(
                    ip.getL()[0] * voxelSize.dimension(0),
                    ip.getL()[1] * voxelSize.dimension(1),
                    ip.getL()[2] * voxelSize.dimension(2)));

            points.add(ip.getL());
        }

        // make the KDTree
        final KDTree<RealPoint> tree = new KDTree<RealPoint>(list1, list1);

        // Nearest neighbor for each point, populate the new list
        final KNearestNeighborSearchOnKDTree<RealPoint> nn = new KNearestNeighborSearchOnKDTree<RealPoint>(tree, 2);

        ArrayList<InterestPoint> interestPoints = new ArrayList<InterestPoint>();

        int id = 0;
        for (int j = 0; j < list2.size(); ++j) {
            final RealPoint p = list2.get(j);
            nn.search(p);

            // first nearest neighbor is the point itself, we need the second nearest
            final double d = nn.getDistance(1);

            if ((keepRange && d >= minDistance && d <= maxDistance) || (!keepRange && (d < minDistance || d > maxDistance))) {
                interestPoints.add(new InterestPoint(id++, points.get(j).clone()));
            }
        }
        return interestPoints;
    }

    /**
     * Thin out.
     *
     * @param spimData the spim data
     * @param viewIds  the view ids
     * @param channels the channels
     * @param saveNewInterestPoints     the saveNewInterestPoints
     * @return the boolean
     */
    @Deprecated
    public static boolean thinOut(final SpimData2 spimData, final List<ViewId> viewIds, final List<ChannelProcessThinOut> channels, final boolean saveNewInterestPoints) {
        final ViewInterestPoints vip = spimData.getViewInterestPoints();

        for (final ChannelProcessThinOut channel : channels) {
            final double minDistance = channel.getMin();
            final double maxDistance = channel.getMax();
            final boolean keepRange = channel.keepRange();
            final String label = channel.getLabel();
            final String newLabel = channel.getNewLabel();

            for (final ViewId viewId : viewIds) {
                final ViewDescription vd = spimData.getSequenceDescription().getViewDescription(viewId);

                if (!vd.isPresent() || vd.getViewSetup().getChannel().getId() != channel.getChannel().getId())
                    continue;

                final ViewInterestPointLists vipl = vip.getViewInterestPointLists(viewId);
                final InterestPointList oldIpl = vipl.getInterestPointList(label);

                if (oldIpl.getInterestPoints() == null)
                    oldIpl.loadInterestPoints();

                final VoxelDimensions voxelSize = vd.getViewSetup().getVoxelSize();
                ArrayList<InterestPoint> filteredInterestPoints = getFilteredInterestPoints(minDistance, maxDistance, keepRange, oldIpl, voxelSize);

                final InterestPointList newIpl = new InterestPointList(
                        oldIpl.getBaseDir(),
                        new File(
                                oldIpl.getFile().getParentFile(),
                                "tpId_" + viewId.getTimePointId() + "_viewSetupId_" + viewId.getViewSetupId() + "." + newLabel));

                newIpl.setInterestPoints(filteredInterestPoints);

                String parameters;
                parameters = getParameterString(minDistance, maxDistance, keepRange, label);

                newIpl.setParameters(parameters);
                vipl.addInterestPointList(newLabel, newIpl);

                IOFunctions.println(new Date(System.currentTimeMillis()) + ": TP=" + vd.getTimePointId() + " ViewSetup=" + vd.getViewSetupId() +
                        ", Detections: " + oldIpl.getInterestPoints().size() + " >>> " + newIpl.getInterestPoints().size());

                if (saveNewInterestPoints && !newIpl.saveInterestPoints()) {
                    IOFunctions.println("Error saving interest point list: " + new File(newIpl.getBaseDir(), newIpl.getFile().toString() + newIpl.getInterestPointsExt()));
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Get parameter string Useful to put into the xml file. See {@link #getParameterString(double, double, boolean, String)}
     *
     * @param parameters the parameters
     * @param label the label
     * @return the string
     */
    public static String getParameterString(RemoveDetectionParameters parameters,String label){
        return getParameterString(parameters.getMin(),parameters.getMax(),parameters.getKeepRange(),label);
    }

    private static String getParameterString(double minDistance, double maxDistance, boolean keepRange, String label) {
        String parameters;
        if (keepRange)
            parameters ="thinned-out '" + label + "', kept range from " + minDistance + " to " + maxDistance;

        else
            parameters ="thinned-out '" + label + "', removed range from " + minDistance + " to " + maxDistance;
        return parameters;
    }


    private static List<InterestPoint> removeDetections(RemoveDetectionParameters removeDetectionParameters, InterestPointList interestPointList, VoxelDimensions vxlDim)
    {
        return getFilteredInterestPoints(removeDetectionParameters.getMin(),removeDetectionParameters.getMax(),removeDetectionParameters.getKeepRange(),interestPointList,vxlDim);
    }

    /**
     * Remove detections.
     *
     * @param spimData2 the spim data 2
     * @param removeDetectionParameters the remove detection parameters
     * @return the hash map
     */
    public static  HashMap<ViewId, List<InterestPoint>> removeDetections(SpimData2 spimData2,RemoveDetectionParameters removeDetectionParameters)
    {
        //print what we do
        if ( removeDetectionParameters.getKeepRange() )
            IOFunctions.println( "Keep only distances from " + removeDetectionParameters.getMin() + " >>> " + removeDetectionParameters.getMax() );
        else
            IOFunctions.println( "Remove distances from " + removeDetectionParameters.getMin() + " >>> " + removeDetectionParameters.getMax() );

        //our data structure to return
        HashMap<ViewId, List<InterestPoint>> resultMap = new HashMap<ViewId, List<InterestPoint>>();

        final ViewInterestPoints vip = spimData2.getViewInterestPoints();
        //iterator over the list of view descriptions
        for (ViewDescription viewDescription:removeDetectionParameters.getToProcess())
        {

            //get the list of interest points
            final ViewInterestPointLists vipl = vip.getViewInterestPointLists(viewDescription);
            final InterestPointList oldIpl = vipl.getInterestPointList(removeDetectionParameters.getLabel());
            //get voxel dimensions
            final ViewDescription vd = spimData2.getSequenceDescription().getViewDescription(viewDescription);
            VoxelDimensions voxDims = vd.getViewSetup().getVoxelSize();
            //put the the interest point list into the result
            resultMap.put(viewDescription, removeDetections(removeDetectionParameters, oldIpl, voxDims));
            //some putput
            IOFunctions.println(new Date(System.currentTimeMillis()) + ": TP=" + vd.getTimePointId() + " ViewSetup=" + vd.getViewSetupId() +
                    ", Detections: " + oldIpl.getInterestPoints().size() + " >>> " + resultMap.get(viewDescription).size());

        }
        return  resultMap;
    }



}
