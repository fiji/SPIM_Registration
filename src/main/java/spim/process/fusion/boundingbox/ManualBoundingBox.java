package spim.process.fusion.boundingbox;

import ij.gui.GenericDialog;

import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalRealInterval;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.process.fusion.export.ImgExport;

public class ManualBoundingBox extends BoundingBox
{	
	public ManualBoundingBox(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess)
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport )
	{
		final double[] minBB = new double[ 3 ];
		final double[] maxBB = new double[ 3 ];
		
		computeMaximalBoundingBox( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess, minBB, maxBB );

		for ( int d = 0; d < minBB.length; ++d )
		{
			BoundingBox.defaultRangeMin[ d ] = (int)Math.floor( minBB[ d ] );
			BoundingBox.defaultRangeMax[ d ] = (int)Math.floor( maxBB[ d ] );
			
			// not preselected
			if ( BoundingBox.defaultMin[ d ] == 0 && BoundingBox.defaultMax[ d ] == 0 )
			{
				BoundingBox.defaultMin[ d ] = BoundingBox.defaultRangeMin[ d ];
				BoundingBox.defaultMax[ d ] = BoundingBox.defaultRangeMax[ d ];
			}
			else if ( BoundingBox.defaultMin[ d ] < BoundingBox.defaultRangeMin[ d ] )
			{
				BoundingBox.defaultMin[ d ] = BoundingBox.defaultRangeMin[ d ];				
			}
			else if ( BoundingBox.defaultMax[ d ] > BoundingBox.defaultRangeMax[ d ] )
			{
				BoundingBox.defaultMax[ d ] = BoundingBox.defaultRangeMax[ d ];								
			}
			
			if ( BoundingBox.defaultMin[ d ] > BoundingBox.defaultMax[ d ] )
			{
				BoundingBox.defaultMin[ d ] = BoundingBox.defaultRangeMin[ d ];
				BoundingBox.defaultMax[ d ] = BoundingBox.defaultRangeMax[ d ];				
			}
		}

		final GenericDialog gd = new GenericDialog( "Manually define Bounding Box" );

		gd.addMessage( "Note: Coordinates are in global coordinates as shown " +
				"in Fiji status bar of a fused datasets", GUIHelper.smallStatusFont );
		
		if ( !fusion.compressBoundingBoxDialog() )
			gd.addMessage( "", GUIHelper.smallStatusFont );
		
		gd.addSlider( "Minimal_X", BoundingBox.defaultRangeMin[ 0 ], BoundingBox.defaultRangeMax[ 0 ], BoundingBox.defaultMin[ 0 ] );
		gd.addSlider( "Minimal_Y", BoundingBox.defaultRangeMin[ 1 ], BoundingBox.defaultRangeMax[ 1 ], BoundingBox.defaultMin[ 1 ] );
		gd.addSlider( "Minimal_Z", BoundingBox.defaultRangeMin[ 2 ], BoundingBox.defaultRangeMax[ 2 ], BoundingBox.defaultMin[ 2 ] );

		if ( !fusion.compressBoundingBoxDialog() )
			gd.addMessage( "" );
		
		gd.addSlider( "Maximal_X", BoundingBox.defaultRangeMin[ 0 ], BoundingBox.defaultRangeMax[ 0 ], BoundingBox.defaultMax[ 0 ] );
		gd.addSlider( "Maximal_Y", BoundingBox.defaultRangeMin[ 1 ], BoundingBox.defaultRangeMax[ 1 ], BoundingBox.defaultMax[ 1 ] );
		gd.addSlider( "Maximal_Z", BoundingBox.defaultRangeMin[ 2 ], BoundingBox.defaultRangeMax[ 2 ], BoundingBox.defaultMax[ 2 ] );

		if ( !fusion.compressBoundingBoxDialog() )
			gd.addMessage( "" );

		if ( fusion.supportsDownsampling() )
			gd.addSlider( "Downsample fused dataset", 1.0, 10.0, BoundingBox.staticDownsampling );
		
		if ( fusion.supports16BitUnsigned() )
			gd.addChoice( "Pixel_type", pixelTypes, pixelTypes[ defaultPixelType ] );
		gd.addChoice( "ImgLib2_container", imgTypes, imgTypes[ defaultImgType ] );
		
		fusion.queryAdditionalParameters( gd );
		imgExport.queryAdditionalParameters( gd, spimData );

		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		Label l1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		Label l2 = (Label)gd.getMessage();

		final ManageListeners m = new ManageListeners( gd, gd.getNumericFields(), gd.getChoices(), l1, l2, fusion, fusion.supportsDownsampling(), fusion.supports16BitUnsigned() );
		fusion.registerAdditionalListeners( m );
		m.update();
		
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
		
		if ( fusion.supports16BitUnsigned() )
			this.pixelType = BoundingBox.defaultPixelType = gd.getNextChoiceIndex();
		else
			this.pixelType = BoundingBox.defaultPixelType = 0; //32-bit
		
		this.imgtype = BoundingBox.defaultImgType = gd.getNextChoiceIndex();
		
		if ( min[ 0 ] > max[ 0 ] || min[ 1 ] > max[ 1 ] || min[ 2 ] > max[ 2 ] )
		{
			IOFunctions.println( "Invalid coordinates, min cannot be larger than max" );
			return false;
		}

		if ( !fusion.parseAdditionalParameters( gd ) )
			return false;

		if ( !imgExport.parseAdditionalParameters( gd, spimData ) )
			return false;

		BoundingBox.defaultMin[ 0 ] = min[ 0 ];
		BoundingBox.defaultMin[ 1 ] = min[ 1 ];
		BoundingBox.defaultMin[ 2 ] = min[ 2 ];
		BoundingBox.defaultMax[ 0 ] = max[ 0 ];
		BoundingBox.defaultMax[ 1 ] = max[ 1 ];
		BoundingBox.defaultMax[ 2 ] = max[ 2 ];

		return true;
	}

	protected static long numPixels( final long[] min, final long[] max, final int downsampling )
	{
		long numpixels = 1;
		
		for ( int d = 0; d < min.length; ++d )
			numpixels *= (max[ d ] - min[ d ])/downsampling;
		
		return numpixels;
	}
	
	public enum Source { IRRELEVANT, MINX, MINY, MINZ, MAXX, MAXY, MAXZ };
	
	public class ManageListeners
	{
		final GenericDialog gd;
		final TextField minX, minY, minZ, maxX, maxY, maxZ, downsample;
		final Choice pixelTypeChoice, imgTypeChoice;
		final Label label1;
		final Label label2;
		final Fusion fusion;
		final boolean supportsDownsampling;
		final boolean supports16bit;

		final long[] min = new long[ 3 ];
		final long[] max = new long[ 3 ];

		public ManageListeners(
				final GenericDialog gd,
				final Vector<?> tf,
				final Vector<?> choices,
				final Label label1,
				final Label label2,
				final Fusion fusion,
				final boolean supportsDownsampling,
				final boolean supports16bit )
		{
			this.gd = gd;
			
			this.minX = (TextField)tf.get( 0 );
			this.minY = (TextField)tf.get( 1 );
			this.minZ = (TextField)tf.get( 2 );
			
			this.maxX = (TextField)tf.get( 3 );
			this.maxY = (TextField)tf.get( 4 );
			this.maxZ = (TextField)tf.get( 5 );
			
			if ( supports16bit )
			{
				pixelTypeChoice = (Choice)choices.get( 0 );
				imgTypeChoice = (Choice)choices.get( 1 );
			}
			else
			{
				pixelTypeChoice = null;
				imgTypeChoice = (Choice)choices.get( 0 );
			}
			
			if ( supportsDownsampling )
				downsample = (TextField)tf.get( 6 );
			else
				downsample = null;
			
			this.label1 = label1;
			this.label2 = label2;
			this.supportsDownsampling = supportsDownsampling;
			this.supports16bit = supports16bit;
			this.fusion = fusion;
			
			this.addListeners();
		}
		
		protected void addListeners()
		{
			this.minX.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update( Source.MINX ); } });
			this.minY.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update( Source.MINY ); } });
			this.minZ.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update( Source.MINZ ); } });
			this.maxX.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update( Source.MAXX ); } });
			this.maxY.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update( Source.MAXY ); } });
			this.maxZ.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update( Source.MAXZ ); } });
			
			this.imgTypeChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });
			
			if ( supportsDownsampling )
				this.downsample.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			
			if ( supports16bit )
				this.pixelTypeChoice.addItemListener( new ItemListener() { @Override
				public void itemStateChanged(ItemEvent e) { update(); } });
		}
		
		public void update() { update( Source.IRRELEVANT ); }
		public void update( final Source s )
		{
			min[ 0 ] = Long.parseLong( minX.getText() );
			min[ 1 ] = Long.parseLong( minY.getText() );
			min[ 2 ] = Long.parseLong( minZ.getText() );

			max[ 0 ] = Long.parseLong( maxX.getText() );
			max[ 1 ] = Long.parseLong( maxY.getText() );
			max[ 2 ] = Long.parseLong( maxZ.getText() );

			// update sliders if necessary
			/*
			if ( min[ 0 ] > max[ 0 ] )
				if ( s == Source.MINX )
					maxX.setText( "" + min[ 0 ] );
				else
					minX.setText( "" + max[ 0 ] );
			
			if ( min[ 1 ] > max[ 1 ] )
				if ( s == Source.MINY )
					maxY.setText( "" + min[ 1 ] );
				else
					minY.setText( "" + max[ 1 ] );

			if ( min[ 2 ] > max[ 2 ] )
				if ( s == Source.MINZ )
					maxZ.setText( "" + min[ 2 ] );
				else
					minZ.setText( "" + max[ 2 ] );
			*/

			if ( supportsDownsampling )
				downsampling = Integer.parseInt( downsample.getText() );
			else
				downsampling = 1;
			
			if ( supports16bit )
				pixelType = pixelTypeChoice.getSelectedIndex();
			else
				pixelType = 0;
			
			imgtype = imgTypeChoice.getSelectedIndex();
			
			
			final long numPixels = numPixels( min, max, downsampling );
			final int bytePerPixel;
			if ( pixelType == 1 )
				bytePerPixel = 2;
			else
				bytePerPixel = 4;
			
			final long megabytes = (numPixels * bytePerPixel) / (1024*1024);
			
			if ( numPixels > Integer.MAX_VALUE && imgtype == 0 )
			{
				label1.setText( megabytes + " MB is too large for ArrayImg!" );
				label1.setForeground( GUIHelper.error );
			}
			else
			{
				label1.setText( "Fused image: " + megabytes + " MB, required total memory ~" + fusion.totalRAM( megabytes, bytePerPixel ) +  " MB" );
				label1.setForeground( GUIHelper.good );
			}
				
			label2.setText( "Dimensions: " + 
					(max[ 0 ] - min[ 0 ] + 1)/downsampling + " x " + 
					(max[ 1 ] - min[ 1 ] + 1)/downsampling + " x " + 
					(max[ 2 ] - min[ 2 ] + 1)/downsampling + " pixels @ " + BoundingBox.pixelTypes[ pixelType ] );			
		}
	}

	public static void computeMaximalBoundingBox(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess,
			final double[] minBB, final double[] maxBB )
	{
		for ( int d = 0; d < minBB.length; ++d )
		{
			minBB[ d ] = Double.MAX_VALUE;
			maxBB[ d ] = -Double.MAX_VALUE;
		}

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Estimating Bounding Box for Fusion. If size of images is not known (they were never opened before), some of them need to be opened once to determine their size.");

		for ( final TimePoint t: timepointsToProcess )
			for ( final Channel c : channelsToProcess )
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						// bureaucracy
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );

						// this happens only if a viewsetup is not present in any timepoint
						// (e.g. after appending fusion to a dataset)
						if ( viewId == null )
							continue;
						
						final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );
		
						if ( !viewDescription.isPresent() )
							continue;

						final Dimensions size = ViewSetupUtils.getSizeOrLoad( viewDescription.getViewSetup(), viewDescription.getTimePoint(), spimData.getSequenceDescription().getImgLoader() );
						final double[] min = new double[]{ 0, 0, 0 };
						final double[] max = new double[]{
								size.dimension( 0 ) - 1,
								size.dimension( 1 ) - 1,
								size.dimension( 2 ) - 1 };
						
						final ViewRegistration r = spimData.getViewRegistrations().getViewRegistration( viewId );
						r.updateModel();
						final FinalRealInterval interval = r.getModel().estimateBounds( new FinalRealInterval( min, max ) );
						
						for ( int d = 0; d < minBB.length; ++d )
						{
							minBB[ d ] = Math.min( minBB[ d ], interval.realMin( d ) );
							maxBB[ d ] = Math.max( maxBB[ d ], interval.realMax( d ) );
						}
					}		
	}

	@Override
	public ManualBoundingBox newInstance(
			final SpimData2 spimData,
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess )
	{
		return new ManualBoundingBox( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Define manually"; }

	@Override
	public void cleanUp( final LoadParseQueryXML result ) {}
}
