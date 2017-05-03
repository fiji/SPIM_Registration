/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package spim.fiji.spimdata.explorer.popup;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import bdv.AbstractSpimSource;
import bdv.BigDataViewer;
import bdv.tools.InitializeViewerState;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Source;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import spim.fiji.plugin.apply.BigDataViewerTransformationWindow;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import spim.fiji.spimdata.imgloaders.StackImgLoader;

public class BDVPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	public ViewSetupExplorerPanel< ?, ? > panel;
	public BigDataViewer bdv = null;

	public BDVPopup()
	{
		super( "Display in BigDataViewer (on/off)" );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
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
					// if BDV was closed by the user
					if ( bdv != null && !bdv.getViewerFrame().isVisible() )
						bdv = null;

					if ( bdv == null )
					{

						try
						{
							bdv = createBDV( panel );
						}
						catch (Exception e)
						{
							IOFunctions.println( "Could not run BigDataViewer: " + e );
							e.printStackTrace();
							bdv = null;
						}
					}
					else
					{
						BigDataViewerTransformationWindow.disposeViewerWindow( bdv );
						bdv = null;
					}
				}
			}).start();
		}
	}

	public static boolean bdvRunning()
	{
		final BDVPopup p = ViewSetupExplorerPanel.bdvPopup();
		return ( p != null && p.bdv != null && p.bdv.getViewerFrame().isVisible() );
	}
	public static BigDataViewer createBDV( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		BasicImgLoader il = panel.getSpimData().getSequenceDescription().getImgLoader();
		if ( AbstractImgLoader.class.isInstance( il ) || StackImgLoader.class.isInstance( il ) )
		{
			if ( JOptionPane.showConfirmDialog( null,
					"Opening <SpimData> dataset that is not suited for interactive browsing.\n" +
					"Consider resaving as HDF5 for better performance.\n" +
					"Proceed anyways?",
					"Warning",
					JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
				return null;
		}

		BigDataViewer bdv = BigDataViewer.open( panel.getSpimData(), panel.xml(), IOFunctions.getProgressWriter(), ViewerOptions.options() );
//		if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should work, but currently tryLoadSettings is protected. fix that.
			InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );

		// do not rotate BDV view by default
		BDVPopup.initTransform( bdv.getViewer() );

		ViewSetupExplorerPanel.updateBDV( bdv, panel.colorMode(), panel.getSpimData(), panel.firstSelectedVD(), panel.selectedRows() );

//		final ArrayList< InterestPointSource > interestPointSources = new ArrayList< InterestPointSource >();
//		interestPointSources.add( new InterestPointSource()
//		{
//			private final ArrayList< RealPoint > points;
//			{
//				points = new ArrayList< RealPoint >();
//				final Random rand = new Random();
//				for ( int i = 0; i < 1000; ++i )
//					points.add( new RealPoint( rand.nextDouble() * 1400, rand.nextDouble() * 800, rand.nextDouble() * 300 ) );
//			}
//
//			@Override
//			public final Collection< ? extends RealLocalizable > getLocalCoordinates( final int timepointIndex )
//			{
//				return points;
//			}
//
//			@Override
//			public void getLocalToGlobalTransform( final int timepointIndex, final AffineTransform3D transform )
//			{
//				transform.identity();
//			}
//		} );
//		final InterestPointOverlay interestPointOverlay = new InterestPointOverlay( bdv.getViewer(), interestPointSources );
//		bdv.getViewer().addRenderTransformListener( interestPointOverlay );
//		bdv.getViewer().getDisplay().addOverlayRenderer( interestPointOverlay );
//		bdv.getViewer().removeTransformListener( interestPointOverlay );
//		bdv.getViewer().getDisplay().removeOverlayRenderer( interestPointOverlay );

		return bdv;
	}

	public void updateBDV()
	{
		if ( bdv == null )
			return;

		for ( final ViewRegistration r : panel.getSpimData().getViewRegistrations().getViewRegistrationsOrdered() )
			r.updateModel();

		final ViewerPanel viewerPanel = bdv.getViewer();
		final ViewerState viewerState = viewerPanel.getState();
		final List< SourceState< ? > > sources = viewerState.getSources();
		
		for ( final SourceState< ? > state : sources )
		{
			Source< ? > source = state.getSpimSource();

			while ( TransformedSource.class.isInstance( source ) )
			{
				source = ( ( TransformedSource< ? > ) source ).getWrappedSource();
			}

			if ( AbstractSpimSource.class.isInstance( source ) )
			{
				final AbstractSpimSource< ? > s = ( AbstractSpimSource< ? > ) source;

				final int tpi = getCurrentTimePointIndex( s );
				callLoadTimePoint( s, tpi );
			}

			if ( state.asVolatile() != null )
			{
				source = state.asVolatile().getSpimSource();
				while ( TransformedSource.class.isInstance( source ) )
				{
					source = ( ( TransformedSource< ? > ) source ).getWrappedSource();
				}

				if ( AbstractSpimSource.class.isInstance( source ) )
				{
					final AbstractSpimSource< ? > s = ( AbstractSpimSource< ? > ) source;

					final int tpi = getCurrentTimePointIndex( s );
					callLoadTimePoint( s, tpi );
				}
			}
		}

		bdv.getViewer().requestRepaint();

	}

	public static void initTransform( final ViewerPanel viewer )
	{
		final Dimension dim = viewer.getDisplay().getSize();
		final ViewerState state = viewer.getState();
		final AffineTransform3D viewerTransform = initTransform( dim.width, dim.height, false, state );
		viewer.setCurrentViewerTransform( viewerTransform );
	}

	public static AffineTransform3D initTransform( final int viewerWidth, final int viewerHeight, final boolean zoomedIn, final ViewerState state )
	{
		final int cX = viewerWidth / 2;
		final int cY = viewerHeight / 2;

		final Source< ? > source = state.getSources().get( state.getCurrentSource() ).getSpimSource();
		final int timepoint = state.getCurrentTimepoint();
		if ( !source.isPresent( timepoint ) )
			return new AffineTransform3D();

		final AffineTransform3D sourceTransform = new AffineTransform3D();
		source.getSourceTransform( timepoint, 0, sourceTransform );

		final Interval sourceInterval = source.getSource( timepoint, 0 );
		final double sX0 = sourceInterval.min( 0 );
		final double sX1 = sourceInterval.max( 0 );
		final double sY0 = sourceInterval.min( 1 );
		final double sY1 = sourceInterval.max( 1 );
		final double sZ0 = sourceInterval.min( 2 );
		final double sZ1 = sourceInterval.max( 2 );
		final double sX = ( sX0 + sX1 + 1 ) / 2;
		final double sY = ( sY0 + sY1 + 1 ) / 2;
		final double sZ = ( sZ0 + sZ1 + 1 ) / 2;

		final double[][] m = new double[ 3 ][ 4 ];

		// NO rotation
		final double[] qViewer = new double[]{ 1, 0, 0, 0 };
		LinAlgHelpers.quaternionToR( qViewer, m );

		// translation
		final double[] centerSource = new double[] { sX, sY, sZ };
		final double[] centerGlobal = new double[ 3 ];
		final double[] translation = new double[ 3 ];
		sourceTransform.apply( centerSource, centerGlobal );
		LinAlgHelpers.quaternionApply( qViewer, centerGlobal, translation );
		LinAlgHelpers.scale( translation, -1, translation );
		LinAlgHelpers.setCol( 3, translation, m );

		final AffineTransform3D viewerTransform = new AffineTransform3D();
		viewerTransform.set( m );

		// scale
		final double[] pSource = new double[] { sX1 + 0.5, sY1 + 0.5, sZ };
		final double[] pGlobal = new double[ 3 ];
		final double[] pScreen = new double[ 3 ];
		sourceTransform.apply( pSource, pGlobal );
		viewerTransform.apply( pGlobal, pScreen );
		final double scaleX = cX / pScreen[ 0 ];
		final double scaleY = cY / pScreen[ 1 ];
		final double scale;
		if ( zoomedIn )
			scale = Math.max( scaleX, scaleY );
		else
			scale = Math.min( scaleX, scaleY );
		viewerTransform.scale( scale );

		// window center offset
		viewerTransform.set( viewerTransform.get( 0, 3 ) + cX, 0, 3 );
		viewerTransform.set( viewerTransform.get( 1, 3 ) + cY, 1, 3 );
		return viewerTransform;
	}

	private static final void callLoadTimePoint( final AbstractSpimSource< ? > s, final int timePointIndex )
	{
		try
		{
			Class< ? > clazz = null;
			boolean found = false;
	
			do
			{
				if ( clazz == null )
					clazz = s.getClass();
				else
					clazz = clazz.getSuperclass();
	
				if ( clazz != null )
					for ( final Method method : clazz.getDeclaredMethods() )
						if ( method.getName().equals( "loadTimepoint" ) )
							found = true;
			}
			while ( !found && clazz != null );
	
			if ( !found )
			{
				System.out.println( "Failed to find SpimSource.loadTimepoint method. Quiting." );
				return;
			}
	
			final Method loadTimepoint = clazz.getDeclaredMethod( "loadTimepoint", Integer.TYPE );
			loadTimepoint.setAccessible( true );
			loadTimepoint.invoke( s, timePointIndex );
		}
		catch ( Exception e ) { e.printStackTrace(); }
	}

	private static final int getCurrentTimePointIndex( final AbstractSpimSource< ? > s )
	{
		try
		{
			Class< ? > clazz = null;
			Field currentTimePointIndex = null;

			do
			{
				if ( clazz == null )
					clazz = s.getClass();
				else
					clazz = clazz.getSuperclass();

				if ( clazz != null )
					for ( final Field field : clazz.getDeclaredFields() )
						if ( field.getName().equals( "currentTimePointIndex" ) )
							currentTimePointIndex = field;
			}
			while ( currentTimePointIndex == null && clazz != null );

			if ( currentTimePointIndex == null )
			{
				System.out.println( "Failed to find AbstractSpimSource.currentTimePointIndex. Quiting." );
				return -1;
			}

			currentTimePointIndex.setAccessible( true );

			return currentTimePointIndex.getInt( s );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return -1;
		}
	}

}
