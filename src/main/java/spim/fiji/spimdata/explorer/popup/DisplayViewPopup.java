package spim.fiji.spimdata.explorer.popup;

import static mpicbg.spim.data.generic.sequence.ImgLoaderHints.LOAD_COMPLETELY;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import ij.gui.GenericDialog;
import mpicbg.imglib.util.Util;
import mpicbg.models.AffineModel3D;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.Display_View;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.TransformView;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.fusion.transformed.TransformWeight;
import spim.process.fusion.weightedavg.ProcessFusion;
import spim.process.fusion.weightedavg.ProcessVirtual;
import spim.vecmath.Transform3D;
import spim.vecmath.Vector3d;
import spim.vecmath.Vector3f;

public class DisplayViewPopup extends JMenu implements ExplorerWindowSetable
{
	public static final int askWhenMoreThan = 5;
	private static final long serialVersionUID = 5234649267634013390L;

	public static double defaultDownsampling = 2.0;
	public static int defaultBB = 0;

	ExplorerWindow< ?, ? > panel = null;
	final JMenu boundingBoxes;

	public DisplayViewPopup()
	{
		super( "Display View(s)" );

		final JMenuItem as32bit = new JMenuItem( "As 32-Bit (Input Image as ImageJ Stack)" );
		final JMenuItem as16bit = new JMenuItem( "As 16-Bit (Input Image as ImageJ Stack)" );
		boundingBoxes = new JMenu( "Virtually Fused" );
		final JMenuItem virtual = new JMenuItem( "Virtually Fused ..." );

		as16bit.addActionListener( new MyActionListener( true ) );
		as32bit.addActionListener( new MyActionListener( false ) );
		virtual.addActionListener( new DisplayVirtualFused( null ) );

		// populate with the current available boundingboxes
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
						final JMenuItem fused = new JMenuItem( bb.getTitle() + " [" + bb.dimension( 0 ) + "x" + bb.dimension( 1 ) + "x" + bb.dimension( 2 ) + "px]" );
						boundingBoxes.add( fused );
						fused.addActionListener( new DisplayVirtualFused( bb ) );
					}
				}
			}

			@Override
			public void menuDeselected( MenuEvent e ) {}

			@Override
			public void menuCanceled( MenuEvent e ) {}
		} );

		this.add( as16bit );
		this.add( as32bit );
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

		public DisplayVirtualFused( final Interval bb )
		{
			this.boundingBox = bb;
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
					final List< ViewId > views = panel.selectedRowsViewId();

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
						gd.addNumericField( "Downsampling", defaultDownsampling, 0 );

						gd.showDialog();

						if ( gd.wasCanceled() )
							return;

						bb = spimData.getBoundingBoxes().getBoundingBoxes().get( defaultBB = gd.getNextChoiceIndex() );
						downsampling = defaultDownsampling = gd.getNextNumber();
						bb = TransformVirtual.scaleBoundingBox( bb, 1.0 / downsampling );
					}
					else
					{
						downsampling = Double.NaN;
						bb = boundingBox;
					}

					final long[] dim = new long[ bb.numDimensions() ];
					bb.dimensions( dim );

					final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
					final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();
					
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

						// TODO: Find next best fitting precomputed scale and scale the image so it looks the same as the full resolution
						final RandomAccessibleInterval inputImg = imgloader.getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );

						openDownsampled(imgloader, model);

						images.add( TransformView.transformView( inputImg, model, bb, 0, 1 ) );
						weights.add( TransformWeight.transformBlending( inputImg, border, blending, model, bb ) );

						//images.add( TransformWeight.transformBlending( inputImg, border, blending, vr.getModel(), bb ) );
						//weights.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( dim ) ) );
					}

					DisplayImage.getImagePlusInstance( new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights ), true, "Fused, Virtual", 0, 255 ).show();
				}
			} ).start();
		}
	}

	private static double length( final float[] a, final float[] b )
	{
		double l = 0;

		for ( int j = 0; j < a.length; ++j )
			l += ( a[ j ] - b[ j ] ) * ( a[ j ] - b[ j ] );

		return Math.sqrt( l );
	}

	private static double[] stepSize( final AffineTransform3D model )
	{
		// have to go from input to output
		// https://github.com/bigdataviewer/bigdataviewer-core/blob/master/src/main/java/bdv/util/MipmapTransforms.java
		final float[] i0 = new float[ 3 ];
		final float[] j0 = new float[ 3 ];

		model.applyInverse( i0, j0 );

		final double[] stepSize = new double[ 3 ];

		for ( int i = 0; i < 3; ++i )
		{
			final float[] i1 = new float[ 3 ];
			final float[] j1 = new float[ 3 ];

			j1[ i ] = 1;

			model.applyInverse( i1, j1 );

			stepSize[ i ] = length( i1, i0 );
			/*
			System.out.print( i + ": ");
			for ( int j = 0; j < 3; ++j )
				System.out.print( i1[ j ] - i0[ j ] + " ");
			System.out.println( " = " + length( i1, i0 ) );*/
		}

		return stepSize;
	}

	private static int findSmallestScale( final int d, final AffineTransform3D m )
	{
		int scale = 1;

		boolean allBiggerOne = true;

		do
		{
			scale *= 2;

			final AffineTransform3D s = new AffineTransform3D();

			if ( d == 0 )
				s.set( scale, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0 );
			else if ( d == 1 )
				s.set( 1.0, 0.0, 0.0, 0.0, 0.0, scale, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0 );
			else if ( d == 2 )
			{
				System.out.println(  scale );
				s.set( 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, scale, 0.0 );
			}

			final AffineTransform3D model = m.copy();
			model.concatenate( s );

			allBiggerOne = true;

			System.out.print( scale + ": ");
			
			for ( final double step : stepSize( model ) )
			{
				System.out.print( step + " ");
				if ( step < 1.0 )
					allBiggerOne = false;
			}

			System.out.println();

		} while ( allBiggerOne );

		return scale/2;
	}


	public static RandomAccessibleInterval openDownsampled( final ImgLoader imgLoader, final AffineTransform3D m )
	{
		//for ( int d = 2; d < 3; ++d )
		//	System.out.print( "\n\n" + findSmallestScale( d, m ) + " " );

		System.out.println();

		
		for ( int scale = 1; scale <= 4; scale *= 2 )
		{
			final AffineTransform3D s = new AffineTransform3D();
			s.set(
					1.0, 0.0, 0.0, 0.0,
					0.0, scale, 0.0, 0.0,
					0.0, 0.0, 1.0, 0.0 );

			System.out.println( "scale: " + scale );

			AffineTransform3D model = m.copy();
			model.concatenate( s );
			
			final float[] i0 = new float[ 3 ];
			final float[] j0 = new float[ 3 ];
	
			model.applyInverse( i0, j0 );
	
			for ( int i = 0; i < 3; ++i )
			{
				final float[] i1 = new float[ 3 ];
				final float[] j1 = new float[ 3 ];
	
				j1[ i ] = 1;

				model.applyInverse( i1, j1 );
	
				System.out.print( i + ": ");
	
				for ( int j = 0; j < 3; ++j )
					System.out.print( i1[ j ] - i0[ j ] + " ");
	
				System.out.println( " = " + length( i1, i0 ) );
			}
	
			System.out.println();
		}
		
		return null;
		/*
		if ( ( dsx > 1 || dsy > 1 || dsz > 1 ) && MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader ) imgLoader;

			double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapResolutions();

			int bestLevel = 0;
			for ( int level = 0; level < mipmapResolutions.length; ++level )
			{
				double[] factors = mipmapResolutions[ level ];
				
				// this fails if factors are not ints
				final int fx = (int)Math.round( factors[ 0 ] );
				final int fy = (int)Math.round( factors[ 1 ] );
				final int fz = (int)Math.round( factors[ 2 ] );
				
				if ( fx <= dsx && fy <= dsy && fz <= dsz && contains( fx, ds ) && contains( fy, ds ) && contains( fz, ds ) )
					bestLevel = level;
			}

			final int fx = (int)Math.round( mipmapResolutions[ bestLevel ][ 0 ] );
			final int fy = (int)Math.round( mipmapResolutions[ bestLevel ][ 1 ] );
			final int fz = (int)Math.round( mipmapResolutions[ bestLevel ][ 2 ] );

			t.set( mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );
			
			dsx /= fx;
			dsy /= fy;
			dsz /= fz;

			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): " +
					"Using precomputed Multiresolution Images [" + fx + "x" + fy + "x" + fz + "], " +
					"Remaining downsampling [" + dsx + "x" + dsy + "x" + dsz + "]" );

			input = (RandomAccessibleInterval< T >) mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), bestLevel, false, LOAD_COMPLETELY );
		}*/
	}
	
	public class MyActionListener implements ActionListener
	{
		final boolean as16bit;

		public MyActionListener( final boolean as16bit )
		{
			this.as16bit = as16bit;
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
					final List< BasicViewDescription< ? extends BasicViewSetup > > vds = panel.selectedRows();

					if (
						vds.size() > askWhenMoreThan &&
						JOptionPane.showConfirmDialog(
							null,
							"Are you sure to display " + vds.size() + " views?",
							"Warning",
							JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
						return;

					IOFunctions.println(
							"Opening as" + ( as16bit ? " 16 bit" : "32 bit" ) + " using " +
							panel.getSpimData().getSequenceDescription().getImgLoader().getClass().getSimpleName() );

					for ( final BasicViewDescription< ? > vd : panel.selectedRows() )
					{
						IOFunctions.println( "Loading timepoint: " + vd.getTimePointId() + " ViewSetup: " + vd.getViewSetupId() );
		
						final String name;
		
						if ( SpimData2.class.isInstance( panel.getSpimData() ) )
							name = Display_View.name( (ViewDescription)vd );
						else
							name = "Timepoint: " + vd.getTimePointId() + " ViewSetup: " + vd.getViewSetupId();
			
						if ( as16bit )
							Display_View.display( panel.getSpimData(), vd, 1, name );
						else
							Display_View.display( panel.getSpimData(), vd, 0, name );
					}
				}
			} ).start();
		}
	}
}
