package spim.headless.Resave;

import java.io.File;

/**
 * Created by schmied on 02/07/15.
 */
public class ResaveHdf5Parameter {

    public boolean setMipmapManual = false;
    public int[][] resolutions;
    public int[][] subdivisions;
    public File seqFile;
    public File hdf5File;
    public boolean deflate = true;
    public boolean split = false;
    public int timepointsPerPartition = 1;
    public int setupsPerPartition =1;
    public boolean onlyRunSingleJob = false;
    public int jobId = 0;

    public int convertChoice = 1;
    public double min = Double.NaN;
    public double max = Double.NaN;

    public  String lastSubsampling = "{1,1,1}, {2,2,1}, {4,4,2}";
    public  String lastChunkSizes = "{16,16,16}, {16,16,16}, {16,16,16}";
    public String getXmlFilename = "one.xml";
    static int lastJobIndex = 0;
    public String exportPath = "/Users/pietzsch/Desktop/spimrec2.xml";

}
