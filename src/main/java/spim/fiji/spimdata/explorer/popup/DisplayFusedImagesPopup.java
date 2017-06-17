package spim.fiji.spimdata.explorer.popup;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import ij.gui.GenericDialog;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.process.export.DisplayImage;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.FusionHelper.ImgDataType;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.TransformView;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.fusion.transformed.TransformWeight;
import spim.process.fusion.weightedavg.ProcessFusion;
import spim.process.fusion.weightedavg.ProcessVirtual;

public class DisplayFusedImagesPopup extends JMenu implements ExplorerWindowSetable
{
	private static final long serialVersionUID = -4895470813542722644L;

	public static int[] quickDownsampling = new int[]{ 1, 2, 3, 4, 8, 16 };
	public static int defaultCache = 0;
	public static int[] cellDim = new int[]{ 10, 10, 10 };
	public static int maxCacheSize = 1000;

	public static double defaultDownsampling = 2.0;
	public static int defaultBB = 0;

	ExplorerWindow< ?, ? > panel = null;
	final JMenu boundingBoxes;

	public DisplayFusedImagesPopup()
	{
		super( "Display Transformed/Fused Image(s)" );

		// populate with the current available boundingboxes
		boundingBoxes = new JMenu( "Quick Display" );
		boundingBoxes.addMenuListener( new MenuListener()
		{
			@Override
			public void menuSelected( MenuEvent e )
			{
				if ( panel != null )
				{
					boundingBoxes.removeAll();

					final SpimData2 spimData = (SpimData2)panel.getSpimData();
					for ( final BoundingBox bb : spimData.getBoundingBoxes().getBoundingBoxes() )
					{
						final JMenu downsampleOptions = new JMenu( bb.getTitle() + " [" + bb.dimension( 0 ) + "x" + bb.dimension( 1 ) + "x" + bb.dimension( 2 ) + "px]" );

						for ( final int ds : quickDownsampling )
						{
							final JMenuItem fused;
							final double downsample;

							if ( ds == 1 )
							{
								fused = new JMenuItem( "Not downsampled" );
								downsample = Double.NaN;
							}
							else
							{
								fused = new JMenuItem( "Downsampled " + ds + "x" );
								downsample = ds;
							}

							fused.addActionListener( new DisplayVirtualFused( bb, downsample, ImgDataType.values()[ defaultCache ] ) );
							downsampleOptions.add( fused );
						}
						boundingBoxes.add( downsampleOptions );
					}

					final JMenuItem cachingState = new JMenuItem( FusionHelper.imgDataTypeChoice[ defaultCache ] );
					cachingState.setForeground( Color.GRAY );
					cachingState.addActionListener( new ChangeCacheState( cachingState ) );
					boundingBoxes.add( cachingState );
				}
			}

			@Override
			public void menuDeselected( MenuEvent e ) {}

			@Override
			public void menuCanceled( MenuEvent e ) {}
		} );

		final JMenuItem virtual = new JMenuItem( "Fuse image(s) ..." );
		virtual.addActionListener( new DisplayVirtualFused( null, Double.NaN, null ) );

		this.add( boundingBoxes );
		this.add( virtual );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = panel;

		return this;
	}

	public class DisplayVirtualFused implements ActionListener
	{
		Interval boundingBox;
		double downsampling = Double.NaN;
		ImgDataType imgType;

		public DisplayVirtualFused( final Interval bb, final double downsampling, final ImgDataType imgType )
		{
			this.boundingBox = bb;
			this.downsampling = downsampling;
			this.imgType = imgType;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final SpimData2 spimData = (SpimData2)panel.getSpimData();
					final List< ViewId > views = ApplyTransformationPopup.getSelectedViews( panel );
					
					Interval bb;

					if ( boundingBox == null )
					{
						if ( spimData.getBoundingBoxes() == null || spimData.getBoundingBoxes().getBoundingBoxes() == null || spimData.getBoundingBoxes().getBoundingBoxes().size() == 0 )
						{
							IOFunctions.println( "No bounding boxes defined, please define one from the menu.");
							return;
						}

						String[] choices = new String[ spimData.getBoundingBoxes().getBoundingBoxes().size() ];
						
						int i = 0;
						for ( final BoundingBox b : spimData.getBoundingBoxes().getBoundingBoxes() )
							choices[ i++ ] = b.getTitle() + " [" + b.dimension( 0 ) + "x" + b.dimension( 1 ) + "x" + b.dimension( 2 ) + "px]";

						if ( defaultBB >= choices.length )
							defaultBB = 0;

						GenericDialog gd = new GenericDialog( "Virtual Fusion" );
						gd.addChoice( "Bounding Box", choices, choices[ defaultBB ] );
						gd.addNumericField( "Downsampling", defaultDownsampling, 1 );
						gd.addChoice( "Caching", FusionHelper.imgDataTypeChoice, FusionHelper.imgDataTypeChoice[ defaultCache ] );

						gd.showDialog();

						if ( gd.wasCanceled() )
							return;

						bb = spimData.getBoundingBoxes().getBoundingBoxes().get( defaultBB = gd.getNextChoiceIndex() );
						downsampling = defaultDownsampling = gd.getNextNumber();
						int caching = defaultCache = gd.getNextChoiceIndex();

						if ( caching == 0 )
							imgType = ImgDataType.VIRTUAL;
						else if ( caching == 1 )
							imgType = ImgDataType.CACHED;
						else
							imgType = ImgDataType.PRECOMPUTED;
					}
					else
					{
						bb = boundingBox;
					}

					if ( !Double.isNaN( downsampling ) )
						bb = TransformVirtual.scaleBoundingBox( bb, 1.0 / downsampling );

					final long[] dim = new long[ bb.numDimensions() ];
					bb.dimensions( dim );

					final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
					final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();

					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Fusing " + views.size() + ", caching strategy=" + imgType );

					for ( final ViewId viewId : views )
					{
						final ImgLoader imgloader = spimData.getSequenceDescription().getImgLoader();
						final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
						vr.updateModel();
						AffineTransform3D model = vr.getModel();

						final float[] blending = ProcessFusion.defaultBlendingRange.clone();
						final float[] border = ProcessFusion.defaultBlendingBorder.clone();

						ProcessVirtual.adjustBlending( spimData.getSequenceDescription().getViewDescription( viewId ), blending, border );

						if ( !Double.isNaN( downsampling ) )
						{
							model = model.copy();
							TransformVirtual.scaleTransform( model, 1.0 / downsampling );
						}

						// this modifies the model so it maps from a smaller image to the global coordinate space,
						// which applies for the image itself as well as the weights since they also use the smaller
						// input image as reference
						final RandomAccessibleInterval inputImg = TransformView.openDownsampled( imgloader, viewId, model );

						images.add( TransformView.transformView( inputImg, model, bb, 0, 1 ) );
						weights.add( TransformWeight.transformBlending( inputImg, border, blending, model, bb ) );

						//images.add( TransformWeight.transformBlending( inputImg, border, blending, vr.getModel(), bb ) );
						//weights.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( dim ) ) );
					}

					RandomAccessibleInterval< FloatType > img = new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights );

					if ( imgType == ImgDataType.CACHED )
						img = FusionHelper.cacheRandomAccessibleInterval( img, maxCacheSize, new FloatType(), cellDim );
					else if ( imgType == ImgDataType.PRECOMPUTED )
						img = FusionHelper.copyImg( img, new ImagePlusImgFactory<>() );

					DisplayImage.getImagePlusInstance( img, true, "Fused, Virtual", 0, 255 ).show();
				}
			} ).start();
		}
	}

	public class ChangeCacheState implements ActionListener
	{
		final JMenuItem m;

		public ChangeCacheState( final JMenuItem m ){ this.m = m; }

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( ++defaultCache > 2 )
				defaultCache = 0;

			m.setText( FusionHelper.imgDataTypeChoice[ defaultCache ]  );
		}
		
	}
}
