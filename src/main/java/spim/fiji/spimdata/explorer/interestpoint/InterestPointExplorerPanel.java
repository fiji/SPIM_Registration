package spim.fiji.spimdata.explorer.interestpoint;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class InterestPointExplorerPanel extends JPanel
{
	private static final long serialVersionUID = -3767947754096099774L;
	
	protected JTable table;
	protected InterestPointTableModel tableModel;
	protected JLabel label;
	
	protected ArrayList< ViewTransform > cache;
	
	public InterestPointExplorerPanel( final ViewInterestPoints viewInterestPoints )
	{
		this.cache = new ArrayList< ViewTransform >();

		initComponent( viewInterestPoints );
	}

	public InterestPointTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }
	
	public void updateViewDescription( final BasicViewDescription< ? > vd )
	{
		if ( label != null )
			this.label.setText("View Description --- Timepoint: " + vd.getTimePointId() + ", View Setup Id: " + vd.getViewSetupId() );

		tableModel.updateViewDescription( vd );
		
		if ( table.getSelectedRowCount() == 0 )
			table.getSelectionModel().setSelectionInterval( 0, 0 );
	}

	public void initComponent( final ViewInterestPoints viewInterestPoints )
	{
		tableModel = new InterestPointTableModel( viewInterestPoints );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		table.setSelectionMode( ListSelectionModel.SINGLE_INTERVAL_SELECTION );
		
		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );
		
		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setCellRenderer( centerRenderer );

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

		addPopupMenu( table );
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

		final ViewInterestPoints vs = tableModel.getViewInterestPoints();

		for ( final int row : selectedRows )
		{
			final String label = InterestPointTableModel.label( vs, vd, row );
			final InterestPointList ipList = vs.getViewInterestPointLists( vd ).getInterestPointList( label );

			IOFunctions.println( "Removing label '' for timepoint_id " + vd.getTimePointId() + " viewsetup_id " + vd.getViewSetupId() + " -- Parsing through all correspondences to remove any links to this interest point list." );

			List< CorrespondingInterestPoints > correspondencesList = ipList.getCorrespondingInterestPoints();

			if ( correspondencesList == null || correspondencesList.size() == 0 )
			{
				if ( ipList.loadCorrespondingInterestPoints() )
					correspondencesList = ipList.getCorrespondingInterestPoints();
			}

			// sort by timepointid, setupid, and detectionid 
			Collections.sort( correspondencesList );

			for ( final CorrespondingInterestPoints c : correspondencesList )
			{
				final ViewId vCorr = c.getCorrespondingViewId();
				final String lCorr = c.getCorrespodingLabel();
				final int idCorr = c.getCorrespondingDetectionId();

				List< InterestPoint > list = ipList.getInterestPoints();

				if ( list == null || list.size() == 0 )
				{
					if ( ipList.loadInterestPoints() )
						list = ipList.getInterestPoints();
				}

			}
		}

		// update everything
		tableModel.fireTableDataChanged();
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