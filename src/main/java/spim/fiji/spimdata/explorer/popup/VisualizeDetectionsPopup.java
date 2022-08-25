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
package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.Visualize_Detections;
import spim.fiji.plugin.Visualize_Detections.Params;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class VisualizeDetectionsPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public VisualizeDetectionsPopup()
	{
		super( "Visualize Interest Points ..." );

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

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final List< ViewId > viewIds = panel.selectedRowsViewId();
					final SpimData2 data = (SpimData2)panel.getSpimData();

					final Params params = Visualize_Detections.queryDetails( data, viewIds );
		
					if ( params == null )
						return;
					
					Visualize_Detections.visualize( data, viewIds, params.channelsToProcess, params.detections, params.downsample, params.displayInput );
				}
			} ).start();
		}
	}
}
