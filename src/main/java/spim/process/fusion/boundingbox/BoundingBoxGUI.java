package spim.process.fusion.boundingbox;

import ij.gui.GenericDialog;

import java.awt.Choice;
import java.awt.Label;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalRealInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ComplexType;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.fusion.export.ImgExport;

public class BoundingBoxGUI extends BoundingBox
{
	public static int staticDownsampling = 1;
	
	public static int defaultMin[] = { 0, 0, 0 };
	public static int defaultMax[] = { 0, 0, 0 };

	public static String[] pixelTypes = new String[]{ "32-bit floating point", "16-bit unsigned integer" };
	public static int defaultPixelType = 0;
	protected int pixelType = 0;

	public static String[] imgTypes = new String[]{ "ArrayImg", "PlanarImg (large images, easy to display)", "CellImg (large images)" };
	public static int defaultImgType = 1;
	protected int imgtype = 1;

	/**
	 * which viewIds to process, set in queryParameters
	 */
	protected final List< ViewId > viewIdsToProcess;

	protected final SpimData2 spimData;

	protected int downsampling = 1;

	protected boolean changedSpimDataObject = false;

	/**
	 * @param spimData
	 * @param viewIdsToProcess - which view ids to fuse
	 */
	public BoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		// init bounding box with no values, means that we will use the default ones for the dialog
		super( null, null );

		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
	}

	public BoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess, final BoundingBox bb )
	{
		// init bounding box with values from bounding box
		super( bb.getMin(), bb.getMax() );

		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
	}

	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport )
	{
		return queryParameters( fusion, imgExport, true );
	}

	/**
	 * Query the necessary parameters for the bounding box
	 * 
	 * @param fusion - the fusion for which the bounding box is computed, can be null
	 * @param imgExport - the export module used, can be null
	 * @return
	 */
	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport, final boolean allowModifyDimensions )
	{
		final boolean compress = fusion == null ? false : fusion.compressBoundingBoxDialog();
		final boolean supportsDownsampling = fusion == null ? false : fusion.supportsDownsampling();
		final boolean supports16BitUnsigned = fusion == null ? false : fusion.supports16BitUnsigned();

		final GenericDialog gd = getSimpleDialog( compress, allowModifyDimensions );

		if ( !compress )
			gd.addMessage( "" );

		if ( supportsDownsampling )
			gd.addSlider( "Downsample fused dataset", 1.0, 10.0, BoundingBoxGUI.staticDownsampling );
		
		if ( supports16BitUnsigned )
			gd.addChoice( "Pixel_type", pixelTypes, pixelTypes[ defaultPixelType ] );

		if ( fusion != null && imgExport != null )
			gd.addChoice( "ImgLib2_container", imgTypes, imgTypes[ defaultImgType ] );

		if ( fusion != null )
			fusion.queryAdditionalParameters( gd );

		if ( imgExport != null )
			imgExport.queryAdditionalParameters( gd, spimData );

		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		Label l1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		Label l2 = (Label)gd.getMessage();

		final ManageListeners m = new ManageListeners( gd, gd.getNumericFields(), gd.getChoices(), l1, l2, fusion, imgExport, supportsDownsampling, supports16BitUnsigned );

		if ( fusion != null )
			fusion.registerAdditionalListeners( m );

		m.update();

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		if ( allowModifyDimensions )
		{
			this.min[ 0 ] = (int)Math.round( gd.getNextNumber() );
			this.min[ 1 ] = (int)Math.round( gd.getNextNumber() );
			this.min[ 2 ] = (int)Math.round( gd.getNextNumber() );
	
			this.max[ 0 ] = (int)Math.round( gd.getNextNumber() );
			this.max[ 1 ] = (int)Math.round( gd.getNextNumber() );
			this.max[ 2 ] = (int)Math.round( gd.getNextNumber() );
		}
		else
		{
			setNFIndex( gd, 6 );
		}

		if ( supportsDownsampling )
			this.downsampling = BoundingBoxGUI.staticDownsampling = (int)Math.round( gd.getNextNumber() );
		else
			this.downsampling = 1;

		if ( supports16BitUnsigned )
			this.pixelType = BoundingBoxGUI.defaultPixelType = gd.getNextChoiceIndex();
		else
			this.pixelType = BoundingBoxGUI.defaultPixelType = 0; //32-bit

		if ( fusion != null && imgExport != null )
			this.imgtype = BoundingBoxGUI.defaultImgType = gd.getNextChoiceIndex();

		if ( min[ 0 ] > max[ 0 ] || min[ 1 ] > max[ 1 ] || min[ 2 ] > max[ 2 ] )
		{
			IOFunctions.println( "Invalid coordinates, min cannot be larger than max" );
			return false;
		}

		if ( fusion != null )
			if ( !fusion.parseAdditionalParameters( gd ) )
				return false;

		if ( imgExport != null )
			if ( !imgExport.parseAdditionalParameters( gd, spimData ) )
				return false;

		BoundingBoxGUI.defaultMin[ 0 ] = min[ 0 ];
		BoundingBoxGUI.defaultMin[ 1 ] = min[ 1 ];
		BoundingBoxGUI.defaultMin[ 2 ] = min[ 2 ];
		BoundingBoxGUI.defaultMax[ 0 ] = max[ 0 ];
		BoundingBoxGUI.defaultMax[ 1 ] = max[ 1 ];
		BoundingBoxGUI.defaultMax[ 2 ] = max[ 2 ];

		return true;
	}

	protected GenericDialog getSimpleDialog( final boolean compress, final boolean allowModifyDimensions )
	{
		final int[] rangeMin = new int[ 3 ];
		final int[] rangeMax = new int[ 3 ];

		setUpDefaultValues( rangeMin, rangeMax );

		final GenericDialog gd = new GenericDialog( "Manually define Bounding Box" );

		gd.addMessage( "Note: Coordinates are in global coordinates as shown " +
				"in Fiji status bar of a fused datasets", GUIHelper.smallStatusFont );

		if ( !compress )
			gd.addMessage( "", GUIHelper.smallStatusFont );

		gd.addSlider( "Minimal_X", rangeMin[ 0 ], rangeMax[ 0 ], this.min[ 0 ] );
		gd.addSlider( "Minimal_Y", rangeMin[ 1 ], rangeMax[ 1 ], this.min[ 1 ] );
		gd.addSlider( "Minimal_Z", rangeMin[ 2 ], rangeMax[ 2 ], this.min[ 2 ] );

		if ( !compress )
			gd.addMessage( "" );

		gd.addSlider( "Maximal_X", rangeMin[ 0 ], rangeMax[ 0 ], this.max[ 0 ] );
		gd.addSlider( "Maximal_Y", rangeMin[ 1 ], rangeMax[ 1 ], this.max[ 1 ] );
		gd.addSlider( "Maximal_Z", rangeMin[ 2 ], rangeMax[ 2 ], this.max[ 2 ] );

		if ( !allowModifyDimensions )
		{
			for ( int i = gd.getSliders().size() - 6; i < gd.getSliders().size(); ++i )
				((Scrollbar)gd.getSliders().get( i )).setEnabled( false );

			for ( int i = gd.getNumericFields().size() - 6; i < gd.getNumericFields().size(); ++i )
				((TextField)gd.getNumericFields().get( i )).setEnabled( false );
		}

		return gd;
	}

	/**
	 * populates this.min[] and this.max[] from the defaultMin and defaultMax
	 *
	 * @param rangeMin - will be populated with the maximal dimension that all views span
	 * @param rangeMax - will be populated with the maximal dimension that all views span
	 */
	protected void setUpDefaultValues( final int[] rangeMin, final int rangeMax[] )
	{
		final double[] minBB = new double[ rangeMin.length ];
		final double[] maxBB = new double[ rangeMin.length ];

		this.changedSpimDataObject = computeMaxBoundingBoxDimensions( spimData, viewIdsToProcess, minBB, maxBB );

		for ( int d = 0; d < minBB.length; ++d )
		{
			rangeMin[ d ] = Math.round( (float)Math.floor( minBB[ d ] ) );
			rangeMax[ d ] = Math.round( (float)Math.floor( maxBB[ d ] ) );

			if ( rangeMin[ d ] < 0 )
				--rangeMin[ d ];

			if ( rangeMax[ d ] > 0 )
				++rangeMax[ d ];

			// first time called on this object
			if ( this.min == null || this.max == null )
			{
				this.min = new int[ rangeMin.length ];
				this.max = new int[ rangeMin.length ];
			}

			if ( this.min[ d ] == 0 && this.max[ d ] == 0 )
			{
				// not preselected
				if ( BoundingBoxGUI.defaultMin[ d ] == 0 && BoundingBoxGUI.defaultMax[ d ] == 0 )
				{
					min[ d ] = rangeMin[ d ];
					max[ d ] = rangeMax[ d ];
				}
				else
				{
					min[ d ] = BoundingBoxGUI.defaultMin[ d ];
					max[ d ] = BoundingBoxGUI.defaultMax[ d ];
				}
			}

			if ( min[ d ] > max[ d ] )
				min[ d ] = max[ d ];

			if ( min[ d ] < rangeMin[ d ] )
				rangeMin[ d ] = min[ d ];

			if ( max[ d ] > rangeMax[ d ] )
				rangeMax[ d ] = max[ d ];

			// test if the values are valid
			//if ( min[ d ] < rangeMin[ d ] )
			//	min[ d ] = rangeMin[ d ];

			//if ( max[ d ] > rangeMax[ d ] )
			//	max[ d ] = rangeMax[ d ];

		}
	}

	/**
	 * @param spimData
	 * @param viewIdsToProcess - which view ids to fuse
	 * @return - a new instance without any special properties
	 */
	public BoundingBoxGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new BoundingBoxGUI( spimData, viewIdsToProcess );
	}

	/**
	 * @return - to be displayed in the generic dialog
	 */
	public String getDescription() { return "Define manually"; }

	/**
	 * Called before the XML is potentially saved
	 *
	 * @return - true if the spimdata was modified, otherwise false
	 */
	public boolean cleanUp() { return changedSpimDataObject; }

	public int getDownSampling() { return downsampling; }

	public int getPixelType() { return pixelType; }

	public int getImgType() { return imgtype; }

	public < T extends ComplexType< T > & NativeType < T > > ImgFactory< T > getImgFactory( final T type )
	{
		final ImgFactory< T > imgFactory;
		
		if ( this.getImgType() == 0 )
			imgFactory = new ArrayImgFactory< T >();
		else if ( this.getImgType() == 1 )
			imgFactory = new ImagePlusImgFactory< T >();
		else
			imgFactory = new CellImgFactory<T>( 256 );

		return imgFactory;
	}

	/**
	 * @return - the final dimensions including downsampling of this bounding box (to instantiate an img)
	 */
	public long[] getDimensions()
	{
		final long[] dim = new long[ this.numDimensions() ];
		this.dimensions( dim );
		
		for ( int d = 0; d < this.numDimensions(); ++d )
			dim[ d ] /= this.getDownSampling();
		
		return dim;
	}

	/**
	 * @param spimData
	 * @param viewIdsToProcess
	 * @param minBB
	 * @param maxBB
	 * @return - true if the SpimData object was modified, otherwise false
	 */
	public static boolean computeMaxBoundingBoxDimensions( final SpimData2 spimData, final List< ViewId > viewIdsToProcess, final double[] minBB, final double[] maxBB )
	{
		for ( int d = 0; d < minBB.length; ++d )
		{
			minBB[ d ] = Double.MAX_VALUE;
			maxBB[ d ] = -Double.MAX_VALUE;
		}

		boolean changed = false;
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Estimating Bounding Box for Fusion. If size of images is not known (they were never opened before), some of them need to be opened once to determine their size.");

		for ( final ViewId viewId : viewIdsToProcess )
		{
			final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
					viewId.getTimePointId(), viewId.getViewSetupId() );

			if ( !viewDescription.isPresent() )
				continue;

			if ( !viewDescription.getViewSetup().hasSize() )
				changed = true;

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

		return changed;
	}

	protected static long numPixels( final long[] min, final long[] max, final int downsampling )
	{
		long numpixels = 1;
		
		for ( int d = 0; d < min.length; ++d )
			numpixels *= (max[ d ] - min[ d ])/downsampling;
		
		return numpixels;
	}

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
				final ImgExport imgExport,
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

				if ( fusion != null && imgExport != null )
					imgTypeChoice = (Choice)choices.get( 1 );
				else
					imgTypeChoice = null;
			}
			else
			{
				pixelTypeChoice = null;
				if ( fusion != null && imgExport != null )
					imgTypeChoice = (Choice)choices.get( 0 );
				else
					imgTypeChoice = null;
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
			
			this.addListeners( imgExport );
		}
		
		protected void addListeners( final ImgExport imgExport )
		{
			this.minX.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.minY.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.minZ.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.maxX.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.maxY.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.maxZ.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });

			if ( fusion != null && imgExport != null )
				this.imgTypeChoice.addItemListener( new ItemListener() { @Override
					public void itemStateChanged(ItemEvent e) { update(); } });

			if ( supportsDownsampling )
				this.downsample.addTextListener( new TextListener() { @Override
					public void textValueChanged(TextEvent e) { update(); } });

			if ( supports16bit )
				this.pixelTypeChoice.addItemListener( new ItemListener() { @Override
					public void itemStateChanged(ItemEvent e) { update(); } });
		}
		
		public void update()
		{
			try
			{
				min[ 0 ] = Long.parseLong( minX.getText() );
				min[ 1 ] = Long.parseLong( minY.getText() );
				min[ 2 ] = Long.parseLong( minZ.getText() );
	
				max[ 0 ] = Long.parseLong( maxX.getText() );
				max[ 1 ] = Long.parseLong( maxY.getText() );
				max[ 2 ] = Long.parseLong( maxZ.getText() );
			}
			catch (Exception e ) {}

			if ( supportsDownsampling )
				downsampling = Integer.parseInt( downsample.getText() );
			else
				downsampling = 1;
			
			if ( supports16bit )
				pixelType = pixelTypeChoice.getSelectedIndex();
			else
				pixelType = 0;

			if ( imgTypeChoice != null )
				imgtype = imgTypeChoice.getSelectedIndex();
			else
				imgtype = 1;

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
				if ( fusion == null )
					label1.setText( "Fused image: " + megabytes + " MB" );
				else
					label1.setText( "Fused image: " + megabytes + " MB, required total memory ~" + fusion.totalRAM( megabytes, bytePerPixel ) +  " MB" );
				label1.setForeground( GUIHelper.good );
			}
				
			label2.setText( "Dimensions: " + 
					(max[ 0 ] - min[ 0 ] + 1)/downsampling + " x " + 
					(max[ 1 ] - min[ 1 ] + 1)/downsampling + " x " + 
					(max[ 2 ] - min[ 2 ] + 1)/downsampling + " pixels @ " + BoundingBoxGUI.pixelTypes[ pixelType ] );
		}
	}

	/**
	 * Increase the counter for GenericDialog.getNextNumber, so we can skip recording it
	 * 
	 * @param gd
	 * @param nfIndex
	 */
	private static final void setNFIndex( final GenericDialog gd, final int nfIndex )
	{
		try
		{
			Class< ? > clazz = null;
			boolean found = false;
	
			do
			{
				if ( clazz == null )
					clazz = gd.getClass();
				else
					clazz = clazz.getSuperclass();
	
				if ( clazz != null )
					for ( final Field field : clazz.getDeclaredFields() )
						if ( field.getName().equals( "nfIndex" ) )
							found = true;
			}
			while ( !found && clazz != null );
	
			if ( !found )
			{
				System.out.println( "Failed to find GenericDialog.nfIndex field. Quiting." );
				return;
			}
	
			final Field nfIndexField = clazz.getDeclaredField( "nfIndex" );
			nfIndexField.setAccessible( true );
			nfIndexField.setInt( gd, nfIndex );
		}
		catch ( Exception e ) { e.printStackTrace(); }
	}

}
