package spim.fiji.plugin.fusion;

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.export.ImgExport;

public class ManageFusionDialogListeners
{
	final GenericDialog gd;
	final TextField downsampleField;
	final Choice boundingBoxChoice, pixelTypeChoice, cachingChoice;
	final Checkbox splitTimepoints, splitChannels;
	final Label label1;
	final Label label2;
	final FusionGUI fusion;

	public ManageFusionDialogListeners(
			final GenericDialog gd,
			final Choice boundingBoxChoice,
			final TextField downsampleField,
			final Choice pixelTypeChoice,
			final Choice cachingChoice,
			final Checkbox splitTimepoints,
			final Checkbox splitChannels,
			final Label label1,
			final Label label2,
			final FusionGUI fusion )
	{
		this.gd = gd;
		this.boundingBoxChoice = boundingBoxChoice;
		this.downsampleField = downsampleField;
		this.pixelTypeChoice = pixelTypeChoice;
		this.cachingChoice = cachingChoice;
		this.splitTimepoints = splitTimepoints;
		this.splitChannels = splitChannels;
		this.label1 = label1;
		this.label2 = label2;
		this.fusion = fusion;

		this.boundingBoxChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.downsampleField.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update(); } });

		this.pixelTypeChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.cachingChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		if ( this.splitTimepoints != null )
			this.splitTimepoints.addItemListener( new ItemListener() { @Override
				public void itemStateChanged(ItemEvent e) { update(); } });

		if ( this.splitChannels != null )
			this.splitChannels.addItemListener( new ItemListener() { @Override
				public void itemStateChanged(ItemEvent e) { update(); } });
	}
	
	public void update()
	{
		fusion.boundingBox = boundingBoxChoice.getSelectedIndex();
		fusion.downsampling = Integer.parseInt( downsampleField.getText() );
		fusion.pixelType = pixelTypeChoice.getSelectedIndex();
		fusion.cacheType = cachingChoice.getSelectedIndex();

		if ( this.splitTimepoints != null )
			fusion.splitTimepoints = this.splitTimepoints.getState();
		else
			fusion.splitTimepoints = false;

		if ( this.splitChannels != null )
			fusion.splitChannels = this.splitChannels.getState();
		else
			fusion.splitChannels = false;

		final BoundingBox bb = fusion.allBoxes.get( fusion.boundingBox );
		final int[] min = bb.getMin();
		final int[] max = bb.getMax();
		final long numPixels = numPixels( min, max, fusion.downsampling );

		final int bytePerPixel;
		if ( fusion.pixelType == 1 )
			bytePerPixel = 2;
		else
			bytePerPixel = 4;

		final long megabytes = (numPixels * bytePerPixel) / (1024*1024);

		label1.setText( "Fused image: " + megabytes + " MB, required total memory ~" + totalRAM( megabytes, bytePerPixel ) +  " MB" );
		label1.setForeground( GUIHelper.good );

		label2.setText( "Dimensions: " + 
				Math.round( (max[ 0 ] - min[ 0 ] + 1)/fusion.downsampling ) + " x " + 
				Math.round( (max[ 1 ] - min[ 1 ] + 1)/fusion.downsampling ) + " x " + 
				Math.round( (max[ 2 ] - min[ 2 ] + 1)/fusion.downsampling ) + " pixels @ " + FusionGUI.pixelTypes[ fusion.pixelType ] );
	}

	public long totalRAM( final long fusedSizeMB, final int bytePerPixel )
	{
		// do we need to load the image data fully?
		long inputImages = 0;

		long processing = 0;

		long overhead = 0;

		if ( fusion.isImgLoaderVirtual() )
		{
			return fusedSizeMB + 100;
		}
		else
		{
			// count input data
			for ( final ViewId viewId : fusion.views )
			{
				
			}

			return fusedSizeMB + 100000;
		}
		/*
		if ( type == WeightedAvgFusionType.FUSEDATA && sequentialViews.getSelectedIndex() == 0 )
			return fusedSizeMB + (getMaxNumViewsPerTimepoint() * (avgPixels/ ( 1024*1024 )) * bytePerPixel);
		else if ( type == WeightedAvgFusionType.FUSEDATA )
			return fusedSizeMB + ((sequentialViews.getSelectedIndex()) * (avgPixels/ ( 1024*1024 )) * bytePerPixel);
		else
			return fusedSizeMB + (avgPixels/ ( 1024*1024 )) * bytePerPixel;
		*/
	}

	protected static long numPixels( final int[] min, final int[] max, final double downsampling )
	{
		long numpixels = 1;
		
		for ( int d = 0; d < min.length; ++d )
			numpixels *= Math.round( (max[ d ] - min[ d ])/downsampling );
		
		return numpixels;
	}
}
