package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.apply.BigDataViewerTransformationWindow;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import bdv.AbstractSpimSource;
import bdv.BigDataViewer;
import bdv.tools.InitializeViewerState;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Source;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;

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
		if ( AbstractImgLoader.class.isInstance( panel.getSpimData().getSequenceDescription().getImgLoader() ) )
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
