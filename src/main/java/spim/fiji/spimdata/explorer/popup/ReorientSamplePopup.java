/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2023 Fiji developers.
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;

import net.imglib2.realtransform.AffineTransform3D;
import bdv.BigDataViewer;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.plugin.apply.ApplyParameters;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class ReorientSamplePopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ViewSetupExplorerPanel< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public ReorientSamplePopup()
	{
		super( "Interactively Reorient Sample ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
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

			if ( !SpimData.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final List< ViewId > viewIds = panel.selectedRowsViewId();
					final SpimData data = (SpimData)panel.getSpimData();
		
					final Apply_Transformation t = new Apply_Transformation();
		
					final ApplyParameters params = new ApplyParameters();
					
					params.sameModelAngles = true;
					params.sameModelChannels = true;
					params.sameModelIlluminations = true;
					params.sameModelTimePoints = true;

					params.model = 2; // rigid
					params.applyTo = 2; // apply on top
					params.defineAs = 2; // not necessary, but means using BDV
					
					final Map< ViewDescription, Pair< double[], String > > modelLinks = t.queryBigDataViewer( data, viewIds, params );

					if ( modelLinks == null )
						return;

					AffineTransform3D applied = new AffineTransform3D();
					applied.set( modelLinks.values().iterator().next().getA() );
					applied = applied.inverse();

					t.applyModels( data, params.minResolution, params.applyTo, modelLinks );

					// update registration panel if available
					panel.updateContent();
					
					// reset current orientation of the BDV so it doesn't jump
					final BigDataViewer bdv = ViewSetupExplorerPanel.bdvPopup().bdv;
					
					if ( bdv != null && bdv.getViewerFrame().isVisible() )
					{
						AffineTransform3D transform = new AffineTransform3D();
						bdv.getViewer().getState().getViewerTransform( transform );

						transform = transform.concatenate( applied );

						bdv.getViewer().setCurrentViewerTransform( transform );
						ViewSetupExplorerPanel.bdvPopup().updateBDV();
					}
				}
			} ).start();
		}
	}
}
