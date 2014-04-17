package spim.fiji.plugin.fusion;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.export.ImgExport;

public abstract class Fusion
{
	public static String[] interpolationTypes = new String[]{ "Nearest Neighbor", "Linear Interpolation" };
	public static int defaultInterpolation = 1;
	protected int interpolation = 1;
	
	public static boolean defaultUseBlending = true;
	protected boolean useBlending = true;

	public static boolean defaultUseContentBased = false;
	protected boolean useContentBased = false;

	/**
	 * which angles to process, set in queryParameters
	 */
	final protected ArrayList< Angle > anglesToProcess;

	/**
	 * which channels to process, set in queryParameters
	 */
	final protected ArrayList< Channel> channelsToProcess;

	/**
	 * which illumination directions to process, set in queryParameters
	 */
	final protected ArrayList< Illumination > illumsToProcess;

	/**
	 * which timepoints to process, set in queryParameters
	 */
	final protected ArrayList< TimePoint > timepointsToProcess;

	final protected SpimData2 spimData;

	/**
	 * @param spimData
	 * @param anglesToPrcoess - which angles to segment
	 * @param channelsToProcess - which channels to segment in
	 * @param illumsToProcess - which illumination directions to segment
	 * @param timepointsToProcess - which timepoints were selected
	 */
	public Fusion(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< Channel> channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		this.spimData = spimData;
		this.anglesToProcess = anglesToProcess;
		this.channelsToProcess = channelsToProcess;
		this.illumsToProcess = illumsToProcess;
		this.timepointsToProcess = timepointsToProcess;
	}
	
	public int getInterpolation() { return interpolation; }
	
	/**
	 * Fuses and saves/displays
	 * 
	 * @param bb
	 * @return
	 */
	public abstract boolean fuseData( final BoundingBox bb, final ImgExport exporter );
	
	public abstract boolean supports16BitUnsigned();
	public abstract boolean supportsDownsampling();
	
	/**
	 * compress the bounding box dialog as much as possible to let more space for extra parameters
	 * @return
	 */
	public abstract boolean compressBoundingBoxDialog();
	
	/**
	 * Query the necessary parameters for the fusion (new dialog has to be made)
	 * 
	 * @return
	 */
	public abstract boolean queryParameters();
	
	/**
	 * Query additional parameters within the bounding box dialog
	 */
	public abstract void queryAdditionalParameters( final GenericDialog gd );

	/**
	 * Parse the additional parameters added before within the bounding box dialog
	 * @param gd
	 * @return
	 */
	public abstract boolean parseAdditionalParameters( final GenericDialog gd );

	/**
	 * @param spimData
	 * @param anglesToPrcoess - which angles to segment
	 * @param channelsToProcess - which channels to segment in
	 * @param illumsToProcess - which illumination directions to segment
	 * @param timepointsToProcess - which timepoints were selected
	 * @return - a new instance without any special properties
	 */
	public abstract Fusion newInstance(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();	
}
