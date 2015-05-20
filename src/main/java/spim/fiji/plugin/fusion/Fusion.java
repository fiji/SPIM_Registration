package spim.fiji.plugin.fusion;

import ij.gui.GenericDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.util.Intervals;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.boundingbox.BoundingBoxGUI.ManageListeners;
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
	 * Maps from an old Viewsetup to a new ViewSetup that is created by the fusion
	 */
	protected Map< ViewSetup, ViewSetup > newViewsetups = null;

	/**
	 * which ViewId to process, set in queryParameters
	 */
	final protected List< ViewId > viewIdsToProcess;

	final protected List< TimePoint > timepointsToProcess;
	final protected List< Channel > channelsToProcess;

	final protected SpimData2 spimData;
	final int maxNumViews;
	final protected long avgPixels;
	
	/**
	 * @param spimData
	 * @param viewIdsToProcess - which viewIds to fuse
	 */
	public Fusion(
			final SpimData2 spimData,
			final List< ViewId > viewIdsToProcess )
	{
		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;

		if ( viewIdsToProcess != null )
		{
			this.timepointsToProcess = SpimData2.getAllTimePointsSorted( spimData, viewIdsToProcess );
			this.channelsToProcess = SpimData2.getAllChannelsSorted( spimData, viewIdsToProcess );
	
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
		else
		{
			this.timepointsToProcess = null;
			this.channelsToProcess = null;
			this.avgPixels = 0;
			this.maxNumViews = 0;
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
	public abstract boolean fuseData( final BoundingBoxGUI bb, final ImgExport exporter );

	/**
	 * @return - which timepoints will be processed, this is maybe inquired by the exporter
	 * if it needs to assemble a new XML dataset or append to the current XML dataset
	 */
	public List< TimePoint > getTimepointsToProcess()
	{
		return timepointsToProcess;
	}

	/**
	 * @return - a sorted List of ViewSetup(s) that are created by this fusion, this maybe requested
	 * if the exporter creates a new XML dataset or appends to an existing XML dataset
	 */
	public List< ViewSetup > getNewViewSetups()
	{
		final ArrayList< ViewSetup > newSetups = new ArrayList< ViewSetup >();
		newSetups.addAll( new HashSet< ViewSetup >( newViewsetups.values() ) );
		Collections.sort( newSetups );
		
		return newSetups;
	}
	
	/**
	 * Set up the list of new viewsetups that are created with this fusion. This maybe required
	 * if the exporter needs to assemble a new XML dataset or append to the current XML dataset.
	 * 
	 * It maps from an old ViewSetup to a new ViewSetup
	 * 
	 * @param bb - the bounding box used for fusing the data
	 * @return the list of new viewsetups (in the order as the viewsetups are processed)
	 */
	protected abstract Map< ViewSetup, ViewSetup > createNewViewSetups( final BoundingBoxGUI bb );
	
	public void defineNewViewSetups( final BoundingBoxGUI bb ) { this.newViewsetups = createNewViewSetups( bb ); }

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
	 * In case there are some other Listener upon whom the memory needs to be recomputed in the Manual Bounding Box.
	 * 
	 * @param m
	 */
	public void registerAdditionalListeners( final ManageListeners m ) {};

	/**
	 * Parse the additional parameters added before within the bounding box dialog
	 * @param gd
	 * @return
	 */
	public abstract boolean parseAdditionalParameters( final GenericDialog gd );

	/**
	 * @param spimData
	 * @param viewIdsToProcess- which viewIds to fuse
	 * @return - a new instance without any special properties
	 */
	public abstract Fusion newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess );
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();
	
	protected long computeAvgImageSize()
	{
		long avgSize = 0;
		int countImgs = 0;

		for ( final ViewId viewId : viewIdsToProcess  )
		{
			final ViewDescription desc = spimData.getSequenceDescription().getViewDescription( viewId );

			if ( desc.isPresent() )
			{
				final ViewSetup viewSetup = desc.getViewSetup();
				final long numPixel = Intervals.numElements( ViewSetupUtils.getSizeOrLoad( viewSetup, desc.getTimePoint(), spimData.getSequenceDescription().getImgLoader() ) );

				avgSize += numPixel;
				++countImgs;
			}
		}
		
		return avgSize / countImgs;
	}

	/**
	 * @return - max num views per fused image
	 */
	protected int computeMaxNumViews()
	{
		int maxViews = 0;
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
			{
				int views = 0;

				for ( final ViewId viewId : viewIdsToProcess )
				{
					final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );
					
					if ( vd.isPresent() && vd.getTimePointId() == t.getId() && vd.getViewSetup().getChannel().getId() == c.getId() )
						++views;
				}
				
				maxViews = Math.max( maxViews, views );
			}
		
		return maxViews;
	}
}
