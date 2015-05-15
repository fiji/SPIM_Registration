package spim.process.fusion.boundingbox;

import ij.gui.GenericDialog;

import java.awt.Label;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.util.List;
import java.util.Vector;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.boundingbox.automatic.MinFilterThreshold;
import spim.process.fusion.export.ImgExport;

public class AutomaticBoundingBox extends BoundingBoxGUI
{
	public static int defaultTimepointIndex = 0;
	public static int defaultChannelIndex = 0;
	public static int defaultDownsamplingAutomatic = 4;
	public static double defaultBackgroundIntensity = 5;
	public static int defaultDiscardedObjectSize = 25;
	public static boolean defaultLoadSequentially = true;
	public static boolean defaultDisplaySegmentationImage = false;

	public AutomaticBoundingBox( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport )
	{
		// first get an idea of the maximal bounding box
		final double[] minBB = new double[ 3 ];
		final double[] maxBB = new double[ 3 ];

		BoundingBoxGUI.computeMaxBoundingBoxDimensions( spimData, viewIdsToProcess, minBB, maxBB );

		// compute dimensions and update size for this instance
		final long[] dim = new long[ maxBB.length ];

		// first time called on this object
		if ( this.min == null || this.max == null )
		{
			this.min = new int[ minBB.length ];
			this.max = new int[ minBB.length ];
		}

		for ( int d = 0; d < dim.length; ++d )
		{
			this.min[ d ] = (int)Math.round( minBB[ d ] );
			this.max[ d ] = (int)Math.round( maxBB[ d ] );
			dim[ d ] = this.max[ d ] - this.min[ d ] + 1;
		}

		final GenericDialog gd = new GenericDialog( "Automatically define Bounding Box" );

		final List< TimePoint > timepointsToProcess = SpimData2.getAllTimePointsSorted( spimData, viewIdsToProcess );
		final List< Channel > channelsToProcess = SpimData2.getAllChannelsSorted( spimData, viewIdsToProcess );

		final String[] timepoints = assembleTimepoints( timepointsToProcess );
		final String[] channels = assembleChannels( channelsToProcess );

		if ( defaultTimepointIndex >= timepoints.length )
			defaultTimepointIndex = 0;

		if ( defaultChannelIndex >= channels.length )
			defaultChannelIndex = 0;
				
		gd.addMessage( "Parameters for automatic segmentation", GUIHelper.largestatusfont );
		
		gd.addChoice( "Timepoint", timepoints, timepoints[ defaultTimepointIndex ] );
		gd.addChoice( "Channel", channels, channels[ defaultChannelIndex ] );
		gd.addSlider( "Background intensity [%]", 1.0, 99.0, defaultBackgroundIntensity );
		gd.addSlider( "Size_of_objects to be discarded", 1, 100, defaultDiscardedObjectSize );
		gd.addMessage( "" );
		gd.addSlider( "Downsampling", 1.0, 10.0, defaultDownsamplingAutomatic );
		gd.addCheckbox( "Load_input_images sequentially", defaultLoadSequentially );
		gd.addCheckbox( "Display_image_used for segmentation", defaultDisplaySegmentationImage );
		gd.addMessage( "Image size: ???x???x??? pixels", GUIHelper.mediumstatusfont, GUIHelper.good );
		Label l = (Label)gd.getMessage();
		
		// add listeners and update values
		addListeners( gd, gd.getNumericFields(), l, dim );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;

		final TimePoint timepoint = timepointsToProcess.get( defaultTimepointIndex = gd.getNextChoiceIndex() );
		final Channel channel = channelsToProcess.get( defaultChannelIndex = gd.getNextChoiceIndex() );
		final double background = defaultBackgroundIntensity = gd.getNextNumber();
		final int discardedObjectSize = defaultDiscardedObjectSize = (int)Math.round( gd.getNextNumber() );

		this.downsampling = defaultDownsamplingAutomatic = (int)Math.round( gd.getNextNumber() );
		final boolean loadSequentially = defaultLoadSequentially = gd.getNextBoolean();
		final boolean displaySegmentationImage = defaultDisplaySegmentationImage = gd.getNextBoolean();
		
		// compute approx bounding box
		final MinFilterThreshold automatic = new MinFilterThreshold(
				spimData,
				viewIdsToProcess,
				channel,
				timepoint,
				this,
				background,
				discardedObjectSize,
				loadSequentially,
				displaySegmentationImage );
		
		if ( !automatic.run() )
		{
			return false;
		}
		else
		{
			this.min = automatic.getMin().clone();
			this.max = automatic.getMax().clone();
			BoundingBoxGUI.defaultMin = automatic.getMin().clone();
			BoundingBoxGUI.defaultMax = automatic.getMax().clone();
		}

		return super.queryParameters( fusion, imgExport );
	}
	
	protected String[] assembleTimepoints( final List< TimePoint > timepoints )
	{
		final String[] t = new String[ timepoints.size() ];
		
		for ( int i = 0; i < timepoints.size(); ++i )
			t[ i ] = timepoints.get( i ).getName();
		
		return t;
	}

	protected String[] assembleChannels( final List< Channel > channels )
	{
		final String[] c = new String[ channels.size() ];
		
		for ( int i = 0; i < channels.size(); ++i )
			c[ i ] = channels.get( i ).getName();
		
		return c;
	}

	protected void addListeners(
			final GenericDialog gd,
			final Vector<?> tf,
			final Label label,
			final long[] dim )
	{
		final TextField downsample = (TextField)tf.get( 2 );

		downsample.addTextListener(
			new TextListener()
			{
				@Override
				public void textValueChanged(TextEvent arg0)
				{
					int downsampling = Integer.parseInt( downsample.getText() );
					
					final long numPixels = numPixels( dim, downsampling );
					final long megabytes = (numPixels * 4) / (1024*1024);
					
					label.setText( "Image size for segmentation: " + 
							(dim[ 0 ])/downsampling + " x " + 
							(dim[ 1 ])/downsampling + " x " + 
							(dim[ 2 ])/downsampling + " pixels, " + megabytes + " MB" );
					label.setForeground( GUIHelper.good );
				}
			} );
	}

	protected static long numPixels( final long[] dim, final int downsampling )
	{
		long numpixels = 1;
		
		for ( int d = 0; d < dim.length; ++d )
			numpixels *= (dim[ d ])/downsampling;
		
		return numpixels;
	}

	@Override
	public AutomaticBoundingBox newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new AutomaticBoundingBox( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Estimate automatically (experimental)";
	}
}
