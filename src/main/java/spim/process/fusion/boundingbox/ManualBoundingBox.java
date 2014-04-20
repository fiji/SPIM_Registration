package spim.process.fusion.boundingbox;

import ij.gui.DialogListener;
import ij.gui.GenericDialog;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.TextEvent;
import java.util.ArrayList;
import java.util.Vector;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.GUIHelper;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.export.ImgExport;

public class ManualBoundingBox extends BoundingBox
{	
	public ManualBoundingBox(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess)
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport )
	{
		final GenericDialog gd = new GenericDialog( "Manually define Bounding Box" );

		gd.addMessage( "Note: Coordinates are in global coordinates as shown " +
				"in Fiji status bar of a fused datasets", GUIHelper.smallStatusFont );
		
		if ( !fusion.compressBoundingBoxDialog() )
			gd.addMessage( "", GUIHelper.smallStatusFont );
		
		gd.addNumericField( "Minimal_X", BoundingBox.minStatic[ 0 ], 0 );
		gd.addNumericField( "Minimal_Y", BoundingBox.minStatic[ 1 ], 0 );
		gd.addNumericField( "Minimal_Z", BoundingBox.minStatic[ 2 ], 0 );

		if ( !fusion.compressBoundingBoxDialog() )
			gd.addMessage( "" );
		
		gd.addNumericField( "Maximal_X", BoundingBox.maxStatic[ 0 ], 0 );
		gd.addNumericField( "Maximal_Y", BoundingBox.maxStatic[ 1 ], 0 );
		gd.addNumericField( "Maximal_Z", BoundingBox.maxStatic[ 2 ], 0 );

		if ( !fusion.compressBoundingBoxDialog() )
			gd.addMessage( "" );

		if ( fusion.supportsDownsampling() )
			gd.addSlider( "Downsample fused dataset", 1.0, 10.0, BoundingBox.staticDownsampling );
		
		String[] pixelTypes;
		if ( fusion.supports16BitUnsigned() )
			pixelTypes = BoundingBox.pixelTypesFull;
		else
			pixelTypes = BoundingBox.pixelTypesHalf;
		
		if ( defaultPixelType >= pixelTypes.length )
			defaultPixelType = 0;
		
		gd.addChoice( "Pixel_type", pixelTypes, pixelTypes[ defaultPixelType ] );
		gd.addChoice( "ImgLib2_container", imgTypes, imgTypes[ defaultImgType ] );
		
		fusion.queryAdditionalParameters( gd );
		imgExport.queryAdditionalParameters( gd );

		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		Label l1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		Label l2 = (Label)gd.getMessage();

		DialogListener d = addListeners( gd, gd.getNumericFields(), gd.getChoices(), l1, l2, fusion.supportsDownsampling() );
		d.dialogItemChanged( gd, new TextEvent( gd, TextEvent.TEXT_VALUE_CHANGED ) );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.min[ 0 ] = (int)Math.round( gd.getNextNumber() );
		this.min[ 1 ] = (int)Math.round( gd.getNextNumber() );
		this.min[ 2 ] = (int)Math.round( gd.getNextNumber() );

		this.max[ 0 ] = (int)Math.round( gd.getNextNumber() );
		this.max[ 1 ] = (int)Math.round( gd.getNextNumber() );
		this.max[ 2 ] = (int)Math.round( gd.getNextNumber() );
		
		if ( fusion.supportsDownsampling() )
			this.downsampling = BoundingBox.staticDownsampling = (int)Math.round( gd.getNextNumber() );
		else
			this.downsampling = 1;
		this.pixelType = BoundingBox.defaultPixelType = gd.getNextChoiceIndex();
		this.imgtype = BoundingBox.defaultImgType = gd.getNextChoiceIndex();
		
		if ( min[ 0 ] > max[ 0 ] || min[ 1 ] > max[ 1 ] || min[ 2 ] > max[ 2 ] )
		{
			IOFunctions.println( "Invalid coordinates, min cannot be larger than max" );
			return false;
		}
		
		if ( !fusion.parseAdditionalParameters( gd ) )
			return false;
		
		if ( !imgExport.parseAdditionalParameters( gd ) )
			return false;
		
		BoundingBox.minStatic[ 0 ] = min[ 0 ];
		BoundingBox.minStatic[ 1 ] = min[ 1 ];
		BoundingBox.minStatic[ 2 ] = min[ 2 ];
		BoundingBox.maxStatic[ 0 ] = max[ 0 ];
		BoundingBox.maxStatic[ 1 ] = max[ 1 ];
		BoundingBox.maxStatic[ 2 ] = max[ 2 ];
		
		return true;
	}

	protected static long numPixels( final long[] min, final long[] max, final int downsampling )
	{
		long numpixels = 1;
		
		for ( int d = 0; d < min.length; ++d )
			numpixels *= (max[ d ] - min[ d ])/downsampling;
		
		return numpixels;
	}
	
	protected DialogListener addListeners(
			final GenericDialog gd,
			final Vector<?> tf,
			final Vector<?> choices,
			final Label label1,
			final Label label2,
			final boolean supportsDownsampling )
	{
		final TextField minX = (TextField)tf.get( 0 );
		final TextField minY = (TextField)tf.get( 1 );
		final TextField minZ = (TextField)tf.get( 2 );
		
		final TextField maxX = (TextField)tf.get( 3 );
		final TextField maxY = (TextField)tf.get( 4 );
		final TextField maxZ = (TextField)tf.get( 5 );
		
		final Choice pixelTypeChoice = (Choice)choices.get( 0 );
		final Choice imgTypeChoice = (Choice)choices.get( 1 );
		
		final TextField downsample;
		if ( supportsDownsampling )
			downsample = (TextField)tf.get( 6 );
		else
			downsample = null;
				
		DialogListener d = new DialogListener()
		{
			final long[] min = new long[ 3 ];
			final long[] max = new long[ 3 ];
			
			int downsampling, pixelType, imgtype;
			
			@Override
			public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
			{
				if ( ( e instanceof TextEvent || e instanceof ItemEvent) && (e.getID() == TextEvent.TEXT_VALUE_CHANGED || e.getID() == ItemEvent.ITEM_STATE_CHANGED ) )
				{
					min[ 0 ] = Long.parseLong( minX.getText() );
					min[ 1 ] = Long.parseLong( minY.getText() );
					min[ 2 ] = Long.parseLong( minZ.getText() );

					max[ 0 ] = Long.parseLong( maxX.getText() );
					max[ 1 ] = Long.parseLong( maxY.getText() );
					max[ 2 ] = Long.parseLong( maxZ.getText() );
					
					if ( supportsDownsampling )
						downsampling = Integer.parseInt( downsample.getText() );
					else
						downsampling = 1;
					pixelType = pixelTypeChoice.getSelectedIndex();
					imgtype = imgTypeChoice.getSelectedIndex();
					
					
					final long numPixels = numPixels( min, max, downsampling );
					final long megabytes;
					
					if ( pixelType == 1 )
						megabytes = (numPixels * 2) / (1024*1024);
					else
						megabytes = (numPixels * 4) / (1024*1024);				
					
					if ( numPixels > Integer.MAX_VALUE && imgtype == 0 )
					{
						label1.setText( megabytes + " MB is too large for ArrayImg!" );
						label1.setForeground( GUIHelper.error );
					}
					else
					{
						label1.setText( "Estimated size: " + megabytes + " MB" );
						label1.setForeground( GUIHelper.good );
					}
						
					label2.setText( "Dimensions: " + 
							(max[ 0 ] - min[ 0 ])/downsampling + " x " + 
							(max[ 1 ] - min[ 1 ])/downsampling + " x " + 
							(max[ 2 ] - min[ 2 ])/downsampling + " pixels @ " + BoundingBox.pixelTypesFull[ pixelType ] );
				}
				return true;
			}			
		};
		
		gd.addDialogListener( d );
		
		return d;
	}

	@Override
	public ManualBoundingBox newInstance(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess )
	{
		return new ManualBoundingBox( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Define manually"; }
}
