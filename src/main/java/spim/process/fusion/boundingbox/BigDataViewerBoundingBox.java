/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2022 Fiji developers.
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
package spim.process.fusion.boundingbox;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JOptionPane;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;

import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;

import bdv.BigDataViewer;
import bdv.tools.InitializeViewerState;
import bdv.tools.boundingbox.BoundingBoxDialog;
import bdv.viewer.ViewerOptions;
import spim.fiji.plugin.apply.BigDataViewerTransformationWindow;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import spim.process.fusion.export.ImgExport;

public class BigDataViewerBoundingBox extends BoundingBoxGUI
{

	public BigDataViewerBoundingBox( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	public static Pair< BigDataViewer, Boolean > getBDV( final AbstractSpimData< ? > spimData, final Collection< ViewId > viewIdsToProcess )
	{
		final BDVPopup popup = ViewSetupExplorerPanel.bdvPopup();
		BigDataViewer bdv;
		boolean bdvIsLocal = false;

		if ( popup == null || popup.panel == null )
		{
			// locally run instance
			if ( AbstractImgLoader.class.isInstance( spimData.getSequenceDescription().getImgLoader() ) )
			{
				if ( JOptionPane.showConfirmDialog( null,
						"Opening <SpimData> dataset that is not suited for interactive browsing.\n" +
						"Consider resaving as HDF5 for better performance.\n" +
						"Proceed anyways?",
						"Warning",
						JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
					return null;
			}

			bdv = BigDataViewer.open( spimData, "BigDataViewer", IOFunctions.getProgressWriter(), ViewerOptions.options() );
			bdvIsLocal = true;

//			if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should work, but currently tryLoadSettings is protected. fix that.
				InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );

			final List< BasicViewDescription< ? > > vds = new ArrayList< BasicViewDescription< ? > >();

			for ( final ViewId viewId : viewIdsToProcess )
				vds.add( spimData.getSequenceDescription().getViewDescriptions().get( viewId ) );

			ViewSetupExplorerPanel.updateBDV( bdv, true, spimData, null, vds );
		}
		else if ( popup.bdv == null )
		{
			// if BDV was closed by the user
			if ( popup.bdv != null && !popup.bdv.getViewerFrame().isVisible() )
				popup.bdv = null;

			try
			{
				bdv = popup.bdv = BDVPopup.createBDV( popup.panel );
			}
			catch (Exception e)
			{
				IOFunctions.println( "Could not run BigDataViewer: " + e );
				e.printStackTrace();
				bdv = popup.bdv = null;
			}
		}
		else
		{
			bdv = popup.bdv;
		}

		return new ValuePair< BigDataViewer, Boolean >( bdv, bdvIsLocal );
	}

	@Override
	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport )
	{
		final Pair< BigDataViewer, Boolean > bdvPair = getBDV( spimData, viewIdsToProcess );
		
		if ( bdvPair == null || bdvPair.getA() == null )
			return false;

		final BigDataViewer bdv = bdvPair.getA();

		// =============== the bounding box dialog ==================
		final AtomicBoolean lock = new AtomicBoolean( false );

		final int[] rangeMin = new int[ 3 ];
		final int[] rangeMax = new int[ 3 ];

		setUpDefaultValues( rangeMin, rangeMax );

		final int boxSetupId = 9999; // some non-existing setup id
		final Interval initialInterval = Intervals.createMinMax( min[ 0 ], min[ 1 ], min[ 2 ], max[ 0 ], max[ 1 ], max[ 2 ] ); // the initially selected bounding box
		final Interval rangeInterval = Intervals.createMinMax(
				rangeMin[ 0 ], rangeMin[ 1 ], rangeMin[ 2 ],
				rangeMax[ 0 ], rangeMax[ 1 ], rangeMax[ 2 ] ); // the range (bounding box of possible bounding boxes)

		final BoundingBoxDialog boundingBoxDialog =
				new BoundingBoxDialog( bdv.getViewerFrame(), "bounding box", bdv.getViewer(), bdv.getSetupAssignments(), boxSetupId, initialInterval, rangeInterval )
		{
			@Override
			public void createContent()
			{
				// button prints the bounding box interval
				final JButton button = new JButton( "ok" );
				button.addActionListener( new AbstractAction()
				{
					private static final long serialVersionUID = 1L;

					@Override
					public void actionPerformed( final ActionEvent e )
					{
						setVisible( false );

						for ( int d = 0; d < min.length; ++ d )
						{
							min[ d ] = (int)Math.floor(boxRealRandomAccessible.getInterval().realMin( d ));
							max[ d ] = (int)Math.ceil(boxRealRandomAccessible.getInterval().realMax( d ));
						}

						lock.set( true );

						try
						{
							synchronized ( lock ) { lock.notifyAll(); }
						}
						catch (Exception e1) {}

					}
				} );

				getContentPane().add( boxSelectionPanel, BorderLayout.NORTH );
				getContentPane().add( button, BorderLayout.SOUTH );
				pack();
			}

			private static final long serialVersionUID = 1L;
		};

		boundingBoxDialog.setVisible( true );

		do
		{
			try
			{
				synchronized ( lock ) { lock.wait(); }
			}
			catch (Exception e) {}
		}
		while ( lock.get() == false );

		IOFunctions.println( "Min: " + Util.printCoordinates( min ) );
		IOFunctions.println( "Max: " + Util.printCoordinates( max ) );

		BoundingBoxGUI.defaultMin = min.clone();
		BoundingBoxGUI.defaultMax = max.clone();

		// was locally opened?
		if ( bdvPair.getB() )
			BigDataViewerTransformationWindow.disposeViewerWindow( bdv );

		return super.queryParameters( fusion, imgExport );
	}

	@Override
	public BigDataViewerBoundingBox newInstance( final SpimData2 spimData, final List<ViewId> viewIdsToProcess )
	{
		return new BigDataViewerBoundingBox( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription() { return "Define with BigDataViewer"; }
}
