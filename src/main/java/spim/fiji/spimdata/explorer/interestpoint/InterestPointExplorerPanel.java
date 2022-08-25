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
package spim.fiji.spimdata.explorer.interestpoint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ViewSetupExplorer;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class InterestPointExplorerPanel extends JPanel
{
	private static final long serialVersionUID = -3767947754096099774L;

	final ViewSetupExplorer< ?, ? > viewSetupExplorer;

	protected JTable table;
	protected InterestPointTableModel tableModel;
	protected JLabel label;

	// when save is called save those files and delete the other ones
	//protected ArrayList< Pair< InterestPointList, ViewId > > save;
	protected ArrayList< Pair< InterestPointList, ViewId > > delete;

	public InterestPointExplorerPanel( final ViewInterestPoints viewInterestPoints, final ViewSetupExplorer< ?, ? > viewSetupExplorer )
	{
		//this.save = new ArrayList< Pair< InterestPointList, ViewId > >();
		this.delete = new ArrayList< Pair< InterestPointList, ViewId > >();

		this.viewSetupExplorer = viewSetupExplorer;
		initComponent( viewInterestPoints );
	}

	public InterestPointTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }
	
	public void updateViewDescription( final BasicViewDescription< ? > vd, final boolean isFirst )
	{
		if ( vd != null && label != null )
			this.label.setText("View Description --- Timepoint: " + vd.getTimePointId() + ", View Setup Id: " + vd.getViewSetupId() );

		if ( vd == null )
			this.label.setText( "No or multiple View Descriptions selected");

		tableModel.updateViewDescription( vd, isFirst );

		if ( table.getSelectedRowCount() == 0 )
			table.getSelectionModel().setSelectionInterval( 0, 0 );
	}

	public void initComponent( final ViewInterestPoints viewInterestPoints )
	{
		tableModel = new InterestPointTableModel( viewInterestPoints, this );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		table.setSelectionMode( ListSelectionModel.SINGLE_INTERVAL_SELECTION );
		
		final MyRenderer myRenderer = new MyRenderer();
		myRenderer.setHorizontalAlignment( JLabel.CENTER );
		
		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setCellRenderer( myRenderer );

		table.setPreferredScrollableViewportSize( new Dimension( 1020, 300 ) );
		final Font f = table.getFont();
		
		table.setFont( new Font( f.getName(), f.getStyle(), 11 ) );
		
		this.setLayout( new BorderLayout() );
		this.label = new JLabel( "View Description --- " );
		this.add( label, BorderLayout.NORTH );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );

		table.getColumnModel().getColumn( 0 ).setPreferredWidth( 30 );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( 5 );
		table.getColumnModel().getColumn( 2 ).setPreferredWidth( 20 );
		table.getColumnModel().getColumn( 3 ).setPreferredWidth( 25 );
		table.getColumnModel().getColumn( 4 ).setPreferredWidth( 400 );

		table.addMouseListener( new MouseListener()
		{
			@Override
			public void mouseReleased(MouseEvent e) {}
			
			@Override
			public void mousePressed(MouseEvent e) {}
			
			@Override
			public void mouseExited(MouseEvent e) {}
			
			@Override
			public void mouseEntered(MouseEvent e) {}
			
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int row = table.rowAtPoint( e.getPoint() );
				int col = table.columnAtPoint( e.getPoint() );

				if ( tableModel.getSelectedRow() == row && tableModel.getSelectedCol() == col )
					tableModel.setSelected( -1, -1 );
				else
					tableModel.setSelected( row, col );

				// update everything
				final int sr = table.getSelectedRow();
				tableModel.fireTableDataChanged();
				table.setRowSelectionInterval( sr, sr );
			}
		});

		addPopupMenu( table );
	}

	protected static class MyRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		Color backgroundColor = getBackground();
		
		@Override
		public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column )
		{
			final Component c = super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column );
			final InterestPointTableModel model = (InterestPointTableModel) table.getModel();

			if ( model.getState( row, column ) )
				c.setBackground( Color.red );
			else if ( !isSelected )
				c.setBackground( backgroundColor );

			return c;
		}
	}

	protected void delete()
	{
		if ( table.getSelectedRowCount() == 0 )
		{
			JOptionPane.showMessageDialog( table, "Nothing selected." );
			return;
		}

		final BasicViewDescription< ? > vd = tableModel.getCurrentViewDescription();

		if ( vd == null )
		{
			JOptionPane.showMessageDialog( table, "No active viewdescription." );
			return;
		}

		final int[] selectedRows = table.getSelectedRows();
		Arrays.sort( selectedRows );

		final ViewInterestPoints vip = tableModel.getViewInterestPoints();

		for ( int rowIndex = selectedRows.length - 1; rowIndex >= 0; rowIndex--)
		{
			final int row = selectedRows[ rowIndex ];

			final String label = InterestPointTableModel.label( vip, vd, row );

			IOFunctions.println( "Removing label '' for timepoint_id " + vd.getTimePointId() + " viewsetup_id " + vd.getViewSetupId() + " -- Parsing through all correspondences to remove any links to this interest point list." );

			final List< CorrespondingInterestPoints > correspondencesList = getCorrespondingInterestPoints( vip, vd, label );

			// sort by timepointid, setupid, and detectionid 
			Collections.sort( correspondencesList );

			ViewId lastViewIdCorr = null;
			//String lastLabelCorr = null;
			List< CorrespondingInterestPoints > cList = null;
			int size = 0;

			for ( final CorrespondingInterestPoints pair : correspondencesList )
			{
				// the next corresponding detection
				final ViewId viewIdCorr = pair.getCorrespondingViewId();
				final String labelCorr = pair.getCorrespodingLabel();
				final int idCorr = pair.getCorrespondingDetectionId();

				// is it a new viewId? The load correspondence list for it
				if ( lastViewIdCorr == null || !lastViewIdCorr.equals( viewIdCorr ) )
				{
					// but first remember the previous list for saving
					if ( lastViewIdCorr != null )
					{
						IOFunctions.println( "Correspondences: " + size + " >>> " + cList.size() );
						//this.save.add( new ValuePair< InterestPointList, ViewId >(
						//				vip.getViewInterestPointLists( lastViewIdCorr ).getInterestPointList( lastLabelCorr ),
						//				lastViewIdCorr ) );
					}

					// remove in the new one
					IOFunctions.println( "Removing correspondences in timepointid=" + viewIdCorr.getTimePointId() + ", viewid=" + viewIdCorr.getViewSetupId() );
					lastViewIdCorr = viewIdCorr;
					//lastLabelCorr = labelCorr;
					cList = getCorrespondingInterestPoints( vip, viewIdCorr, labelCorr );
					size = cList.size();
				}

				// find the counterpart in the list that corresponds with pair.getDetectionId() and vd
				for ( int i = 0; i < cList.size(); ++i )
				{
					final CorrespondingInterestPoints cc = cList.get( i );
					
					if ( cc.getDetectionId() == idCorr && cc.getCorrespondingDetectionId() == pair.getDetectionId() && cc.getCorrespondingViewId().equals( vd ) )
					{
						// remove it here
						cList.remove( i );
						break;
					}
				}
			}

			// remember the list for saving
			if ( lastViewIdCorr != null )
			{
				IOFunctions.println( "Correspondences: " + size + " >>> " + cList.size() );
				//this.save.add( new ValuePair< InterestPointList, ViewId >(
				//				vip.getViewInterestPointLists( lastViewIdCorr ).getInterestPointList( lastLabelCorr ),
				//				lastViewIdCorr ) );
			}

			// remember to deleted the files
			this.delete.add( new ValuePair< InterestPointList, ViewId >(
					vip.getViewInterestPointLists( vd ).getInterestPointList( label ),
					vd ) );

			vip.getViewInterestPointLists( vd ).getHashMap().remove( label );
		}

		// reset selection
		tableModel.setSelected( -1, -1 );

		// update everything
		tableModel.fireTableDataChanged();
	}

	public List< InterestPoint > getInterestPoints( final ViewInterestPoints vip, final ViewId v, final String label )
	{
		final InterestPointList ipList = vip.getViewInterestPointLists( v ).getInterestPointList( label );
		List< InterestPoint > list = ipList.getInterestPoints();

		if ( list == null )
		{
			ipList.loadInterestPoints();
			list = ipList.getInterestPoints();
		}

		return list;
	}

	public List< CorrespondingInterestPoints > getCorrespondingInterestPoints( final ViewInterestPoints vip, final ViewId v, final String label )
	{
		final InterestPointList ipList = vip.getViewInterestPointLists( v ).getInterestPointList( label );
		List< CorrespondingInterestPoints > correspondencesList = ipList.getCorrespondingInterestPoints();

		if ( correspondencesList == null )
		{
			ipList.loadCorrespondingInterestPoints();
			correspondencesList = ipList.getCorrespondingInterestPoints();
		}

		return correspondencesList;
	}

	protected void addPopupMenu( final JTable table )
	{
		final JPopupMenu popupMenu = new JPopupMenu();
		final JMenuItem deleteItem = new JMenuItem( "Delete" );

		deleteItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				delete();
				System.out.println( "Right-click performed on table and choose DELETE" );
			}
		});

		popupMenu.add( deleteItem );

		table.setComponentPopupMenu( popupMenu );
	}
}
