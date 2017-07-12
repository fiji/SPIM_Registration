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
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.process.boundingbox.BoundingBoxTools;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.FusionHelper.ImgDataType;
import spim.process.fusion.FusionTools;

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

					final ArrayList< ViewId > views = new ArrayList<>();
					views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					List< ViewId > removed = SpimData2.filterMissingViews( panel.getSpimData(), views );
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

					for ( final BoundingBox bb : BoundingBoxTools.getAllBoundingBoxes( spimData, views, true ) )
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

					boundingBoxes.add( new Separator() );

					final JMenuItem[] items = new JMenuItem[ FusionHelper.imgDataTypeChoice.length ];

					for ( int i = 0; i < items.length; ++i )
					{
						final JMenuItem item = new JMenuItem( FusionHelper.imgDataTypeChoice[ i ] );

						if ( i == defaultCache )
							item.setForeground( Color.RED );
						else
							item.setForeground( Color.GRAY );

						items[ i ] = item;
					}

					for ( int i = 0; i < items.length; ++i )
					{
						final JMenuItem item = items[ i ];
						item.addActionListener( new ChangeCacheState( items, i ) );
						boundingBoxes.add( item );
					}

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

					final ArrayList< ViewId > views = new ArrayList<>();
					views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					List< ViewId > removed = SpimData2.filterMissingViews( panel.getSpimData(), views );
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

					Interval bb;

					if ( boundingBox == null )
					{
						if ( spimData.getBoundingBoxes() == null || spimData.getBoundingBoxes().getBoundingBoxes() == null || spimData.getBoundingBoxes().getBoundingBoxes().size() == 0 )
						{
							IOFunctions.println( "No bounding boxes defined, please define one from the menu.");
							return;
						}

						final List< BoundingBox > allBoxes = BoundingBoxTools.getAllBoundingBoxes( spimData, views, true );
						final String[] choices = new String[ allBoxes.size() ];

						int i = 0;
						for ( final BoundingBox b : allBoxes )
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

						bb = allBoxes.get( defaultBB = gd.getNextChoiceIndex() );
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

					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Fusing " + views.size() + ", downsampling=" + downsampling + ", caching strategy=" + imgType );

					FusionTools.display( FusionTools.fuseVirtual( spimData, views, true, bb, downsampling ), imgType ).show();
				}
			} ).start();
		}
	}

	public class ChangeCacheState implements ActionListener
	{
		final JMenuItem[] items;
		final int myState;

		public ChangeCacheState( final JMenuItem[] items, final int myState )
		{
			this.items = items;
			this.myState = myState;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			for ( int i = 0; i < items.length; ++i )
			{
				if ( i == myState )
					items[ i ].setForeground( Color.RED );
				else
					items[ i ].setForeground( Color.GRAY );
			}

			defaultCache = myState;
		}
		
	}
}
