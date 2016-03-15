package spim.fiji.spimdata.explorer.registration;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;

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
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import net.imglib2.realtransform.AffineTransform3D;

public class RegistrationExplorerPanel extends JPanel
{
	private static final long serialVersionUID = -3767947754096099774L;
	
	final RegistrationExplorer< ?, ? > explorer;
	
	protected JTable table;
	protected RegistrationTableModel tableModel;
	protected JLabel label;
	
	protected ArrayList< ViewTransform > cache;
	
	public RegistrationExplorerPanel( final ViewRegistrations viewRegistrations, final RegistrationExplorer< ?, ? > explorer )
	{
		this.cache = new ArrayList< ViewTransform >();
		this.explorer = explorer;

		initComponent( viewRegistrations );
	}

	public RegistrationTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }
	
	public void updateViewDescription( final BasicViewDescription< ? > vd )
	{
		if ( vd != null && label != null )
			this.label.setText( "View Description --- Timepoint: " + vd.getTimePointId() + ", View Setup Id: " + vd.getViewSetupId() );

		if ( vd == null )
			this.label.setText( "No or multiple View Descriptions selected");

		tableModel.updateViewDescription( vd );
		
		if ( table.getSelectedRowCount() == 0 )
			table.getSelectionModel().setSelectionInterval( 0, 0 );
	}

	public void initComponent( final ViewRegistrations viewRegistrations )
	{
		tableModel = new RegistrationTableModel( viewRegistrations, this );

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
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( 300 );
		for ( int i = 1; i < table.getColumnCount(); ++i )
			table.getColumnModel().getColumn( i ).setPreferredWidth( 100 );
		final Font f = table.getFont();
		
		table.setFont( new Font( f.getName(), f.getStyle(), 11 ) );
		
		this.setLayout( new BorderLayout() );
		this.label = new JLabel( "View Description --- " );
		this.add( label, BorderLayout.NORTH );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );
		
		addPopupMenu( table );
	}

	protected void copySelection()
	{
		cache.clear();
		
		if ( table.getSelectedRowCount() == 0 )
		{
			JOptionPane.showMessageDialog( table, "Nothing selected");
			return;
		}
		else
		{
			final BasicViewDescription< ? > vd = tableModel.getCurrentViewDescription();
			
			if ( vd == null )
			{
				JOptionPane.showMessageDialog( table, "No active viewdescription." );
				return;
			}
			
			final ViewRegistration vr = tableModel.getViewRegistrations().getViewRegistration( vd );
			
			for ( int row : table.getSelectedRows() )
			{
				cache.add( duplicate( vr.getTransformList().get( row ) ) );
				System.out.println( "Copied row " + vr.getTransformList().get( row ).getName() );
			}
		}
	}

	/**
	 * 
	 * @param type 0 == before, 1 == replace, 2 == after
	 */
	protected void pasteSelection( final int type )
	{
		if ( cache.size() == 0 )
		{
			JOptionPane.showMessageDialog( table, "Nothing copied so far." );
			return;
		}
		
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

		final ViewRegistration vr = tableModel.getViewRegistrations().getViewRegistration( vd );

		// check out where to start inserting
		final int[] selectedRows = table.getSelectedRows();
		Arrays.sort( selectedRows );
		int insertAt;
		
		if ( type == 0 )
		{
			insertAt = selectedRows[ 0 ];
		}
		else if ( type == 1 )
		{
			insertAt = selectedRows[ 0 ];

			// remove the selected entries
			for ( int i = selectedRows[ selectedRows.length - 1 ]; i >= selectedRows[ 0 ]; --i )
				vr.getTransformList().remove( i );
		}
		else
		{
			insertAt = selectedRows[ selectedRows.length - 1 ] + 1;
		}

		// add the new entries
		final ArrayList< ViewTransform > newList = new ArrayList< ViewTransform >();
		
		// add the old entries
		for ( int i = 0; i < insertAt; ++i )
			newList.add( vr.getTransformList().get( i ) );
		
		// add the copied ones
		for ( int i = 0; i < cache.size(); ++i )
			newList.add( duplicate( cache.get( i ) ) );
		
		// add the rest
		for ( int i = insertAt; i < vr.getTransformList().size(); ++i )
			newList.add( vr.getTransformList().get( i ) );
		
		vr.getTransformList().clear();
		vr.getTransformList().addAll( newList );
		vr.updateModel();

		// update everything
		tableModel.fireTableDataChanged();
	}
	
	protected static ViewTransform duplicate( final ViewTransform vt )
	{
		final AffineTransform3D t = new AffineTransform3D();
		t.set( vt.asAffine3D().getRowPackedCopy() );
		
		return new ViewTransformAffine( vt.getName(), t );
	}

	protected static ViewTransform newName( final ViewTransform vt, final String name )
	{
		final AffineTransform3D t = new AffineTransform3D();
		t.set( vt.asAffine3D().getRowPackedCopy() );
		
		return new ViewTransformAffine( name, t );
	}

	protected static ViewTransform newMatrixEntry( final ViewTransform vt, final double value, final int index )
	{
		final AffineTransform3D t = new AffineTransform3D();
		final double[] m = vt.asAffine3D().getRowPackedCopy();
		m[ index ] = value;
		t.set( m );
		
		return new ViewTransformAffine( vt.getName(), t );
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

		final ViewRegistration vr = tableModel.getViewRegistrations().getViewRegistration( vd );

		for ( int i = selectedRows[ selectedRows.length - 1 ]; i >= selectedRows[ 0 ]; --i )
			vr.getTransformList().remove( i );

		if  ( vr.getTransformList().isEmpty() )
			vr.getTransformList().add( new ViewTransformAffine( null, new AffineTransform3D() ) );

		vr.updateModel();

		// update everything
		tableModel.fireTableDataChanged();
	}
	
	protected void addPopupMenu( final JTable table )
	{
		final JPopupMenu popupMenu = new JPopupMenu();
		
		JMenuItem copyItem = new JMenuItem( "Copy" );
		JMenuItem deleteItem = new JMenuItem( "Delete" );
		
		JMenuItem pasteBeforeItem = new JMenuItem( "Paste before selection" );
		JMenuItem pasteAndRepaceItem = new JMenuItem( "Paste and replace selection" );
		JMenuItem pasteAfterItem = new JMenuItem( "Paste after selection" );
		
		copyItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				copySelection();
			}
		});

		pasteBeforeItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				pasteSelection( 0 );
			}
		});

		pasteAndRepaceItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				pasteSelection( 1 );
			}
		});

		pasteAfterItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				pasteSelection( 2 );
			}
		});

		deleteItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				delete();
				System.out.println( "Right-click performed on table and choose DELETE" );
			}
		});
		
		popupMenu.add( copyItem );
		popupMenu.add( pasteBeforeItem );
		popupMenu.add( pasteAndRepaceItem );
		popupMenu.add( pasteAfterItem );
		popupMenu.add( deleteItem );
		
		table.setComponentPopupMenu( popupMenu );
	}
}
