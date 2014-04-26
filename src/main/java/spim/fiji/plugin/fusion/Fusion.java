package spim.fiji.plugin.fusion;

import ij.gui.GenericDialog;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
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
	final int maxNumViews;
	final protected long avgPixels;
	
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
		
		if ( spimData == null )
		{
			avgPixels = 0;
			maxNumViews = 0;
		}
		else
		{
			avgPixels = computeAvgImageSize();
			maxNumViews = computeMaxNumViews();
		}
	}
	
	public abstract long totalRAM( final long fusedSizeMB, final int bytePerPixel );
	
	public int getMaxNumViewsPerTimepoint() { return maxNumViews; }
	
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
	
	protected long computeAvgImageSize()
	{
		long avgSize = 0;
		int countImgs = 0;
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
				for ( final Angle a : anglesToProcess )
					for ( final Illumination i : illumsToProcess )
					{
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );
						final ViewDescription<TimePoint, ViewSetup> desc = spimData.getSequenceDescription().getViewDescription( viewId );
						
						if ( desc.isPresent() )
						{
							final ViewSetup viewSetup = desc.getViewSetup();
							final long numPixel = viewSetup.getWidth() * viewSetup.getHeight() * viewSetup.getDepth();
							
							avgSize += numPixel;
							++countImgs;
						}
					}
		
		return avgSize / countImgs;
	}

	protected int computeMaxNumViews()
	{
		int maxViews = 0;
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
			{
				int views = 0;
				
				for ( final Angle a : anglesToProcess )
					for ( final Illumination i : illumsToProcess )
					{
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );
						final ViewDescription<TimePoint, ViewSetup> desc = spimData.getSequenceDescription().getViewDescription( viewId );
						
						if ( desc.isPresent() )
							++views;
					}
				
				maxViews = Math.max( maxViews, views );
			}
		
		return maxViews;
	}
}
