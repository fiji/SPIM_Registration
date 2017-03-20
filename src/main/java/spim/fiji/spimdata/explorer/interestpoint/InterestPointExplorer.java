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
package spim.fiji.spimdata.explorer.interestpoint;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.JFrame;

import bdv.BigDataViewer;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.explorer.ViewSetupExplorer;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.interestpoints.InterestPointList;

public class InterestPointExplorer< AS extends SpimData2, X extends XmlIoAbstractSpimData< ?, AS > >
	implements SelectedViewDescriptionListener< AS >
{
	final String xml;
	final JFrame frame;
	final InterestPointExplorerPanel panel;
	final ViewSetupExplorer< AS, X > viewSetupExplorer;

	public InterestPointExplorer( final String xml, final X io, final ViewSetupExplorer< AS, X > viewSetupExplorer )
	{
		this.xml = xml;
		this.viewSetupExplorer = viewSetupExplorer;

		frame = new JFrame( "Interest Point Explorer" );
		panel = new InterestPointExplorerPanel( viewSetupExplorer.getPanel().getSpimData().getViewInterestPoints(), viewSetupExplorer );
		frame.add( panel, BorderLayout.CENTER );

		frame.setSize( panel.getPreferredSize() );

		frame.pack();
		frame.setVisible( true );
		
		// Get the size of the screen
		final Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

		// Move the window
		frame.setLocation( ( dim.width - frame.getSize().width ) / 2, ( dim.height - frame.getSize().height ) / 4 );

		// this call also triggers the first update of the registration table
		viewSetupExplorer.addListener( this );

		frame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				quit();
				e.getWindow().dispose();
			}
		});
	}

	public JFrame frame() { return frame; }

	@Override
	public void seletedViewDescription( final BasicViewDescription<? extends BasicViewSetup> viewDescription )
	{
		panel.updateViewDescription( viewDescription, false );
	}

	@Override
	public void save()
	{
		for ( final Pair< InterestPointList, ViewId > list : panel.delete )
		{
			IOFunctions.println( "Deleting correspondences and interestpoints in timepointid=" + list.getB().getTimePointId() + ", viewid=" + list.getB().getViewSetupId() );

			final File ip = new File( list.getA().getBaseDir(), list.getA().getFile().toString() + list.getA().getInterestPointsExt() );
			final File corr = new File( list.getA().getBaseDir(), list.getA().getFile().toString() + list.getA().getCorrespondencesExt() );

			if ( ip.delete() )
				IOFunctions.println( "Deleted: " + ip.getAbsolutePath() );
			else
				IOFunctions.println( "FAILED to delete: " + ip.getAbsolutePath() );

			if ( corr.delete() )
				IOFunctions.println( "Deleted: " + corr.getAbsolutePath() );
			else
				IOFunctions.println( "FAILED to delete: " + corr.getAbsolutePath() );
		}

		//panel.save.clear();
		panel.delete.clear();
	}

	@Override
	public void quit()
	{
		if ( BDVPopup.bdvRunning() && panel.tableModel.interestPointOverlay != null )
		{
			final BigDataViewer bdv = ViewSetupExplorerPanel.bdvPopup().bdv;
			bdv.getViewer().removeTransformListener( panel.tableModel.interestPointOverlay );
			bdv.getViewer().getDisplay().removeOverlayRenderer( panel.tableModel.interestPointOverlay );
			ViewSetupExplorerPanel.bdvPopup().updateBDV();
		}

		frame.setVisible( false );
		frame.dispose();
	}

	public InterestPointExplorerPanel panel() { return panel; }

	@Override
	public void updateContent( final AS data )
	{
		panel.getTableModel().update( data.getViewInterestPoints() );
		panel.getTableModel().fireTableDataChanged();
	}
}
