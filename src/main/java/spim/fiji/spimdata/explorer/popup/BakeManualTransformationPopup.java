package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.JMenuItem;

import bdv.AbstractSpimSource;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class BakeManualTransformationPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 4627408819269954486L;

	ViewSetupExplorerPanel< ?, ? > panel;

	public BakeManualTransformationPopup()
	{
		super( "Bake BDV manual transform" );

		this.addActionListener( this::actionPerformed );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	private void actionPerformed( final ActionEvent e )
	{
		if ( panel == null )
		{
			IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
			return;
		}

		final List< ViewId > viewIds = panel.selectedRowsViewId();

		final ViewRegistrations vr = panel.getSpimData().getViewRegistrations();
		ViewerState state = ViewSetupExplorerPanel.bdvPopup().bdv.getViewer().getState();
		for ( SourceState< ? > s : state.getSources() )
		{
			if ( s.getSpimSource() instanceof TransformedSource )
			{
				TransformedSource< ? > transformedSource = ( TransformedSource< ? > ) s.getSpimSource();
				if ( transformedSource.getWrappedSource() instanceof AbstractSpimSource )
				{
					int setupId = ( ( AbstractSpimSource< ? > ) transformedSource.getWrappedSource() ).getSetupId();

					AffineTransform3D manual = new AffineTransform3D();
					transformedSource.getFixedTransform( manual );

					for ( final ViewId viewId : viewIds )
					{
						if ( viewId.getViewSetupId() == setupId )
						{
							final ViewRegistration v = vr.getViewRegistrations().get( viewId );
							final ViewTransform vt = new ViewTransformAffine( "baked bdv manual transform", manual );
							v.preconcatenateTransform( vt );
//							v.updateModel();
						}
					}
				}
				transformedSource.setFixedTransform( new AffineTransform3D() );
			}
		}

		panel.updateContent();
		ViewSetupExplorerPanel.bdvPopup().updateBDV();
	}
}
