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
