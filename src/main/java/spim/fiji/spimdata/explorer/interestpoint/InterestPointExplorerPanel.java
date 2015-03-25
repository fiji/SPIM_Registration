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
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.ImgLib2Temp.ValuePair;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class InterestPointExplorerPanel extends JPanel
{
	private static final long serialVersionUID = -3767947754096099774L;
	
	protected JTable table;
	protected InterestPointTableModel tableModel;
	protected JLabel label;

	// when save is called save those files and delete the other ones
	//protected ArrayList< Pair< InterestPointList, ViewId > > save;
	protected ArrayList< Pair< InterestPointList, ViewId > > delete;

	public InterestPointExplorerPanel( final ViewInterestPoints viewInterestPoints )
	{
		//this.save = new ArrayList< Pair< InterestPointList, ViewId > >();
		this.delete = new ArrayList< Pair< InterestPointList, ViewId > >();

		initComponent( viewInterestPoints );
	}

	public InterestPointTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }
	
	public void updateViewDescription( final BasicViewDescription< ? > vd )
	{
		if ( vd != null && label != null )
			this.label.setText("View Description --- Timepoint: " + vd.getTimePointId() + ", View Setup Id: " + vd.getViewSetupId() );

		if ( vd == null )
			this.label.setText( "No or multiple View Descriptions selected");

		tableModel.updateViewDescription( vd );

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

		final ViewInterestPoints vip = tableModel.getViewInterestPoints();

		for ( final int row : selectedRows )
		{
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