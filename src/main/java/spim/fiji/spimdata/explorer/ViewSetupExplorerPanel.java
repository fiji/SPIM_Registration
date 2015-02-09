package spim.fiji.spimdata.explorer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import javax.swing.DefaultListSelectionModel;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.io.IOFunctions;

public class ViewSetupExplorerPanel< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > > extends JPanel
{
	private static final long serialVersionUID = -3767947754096099774L;
	
	protected JTable table;
	protected ViewSetupTableModel< AS > tableModel;
	protected ArrayList< SelectedViewDescriptionListener > listeners;
	protected AS data;
	final String xml;
	final X io;

	public ViewSetupExplorerPanel( final AS data, final String xml, final X io )
	{
		this.listeners = new ArrayList< SelectedViewDescriptionListener >();
		this.data = data;
		this.xml = xml;
		this.io = io;

		initComponent();
	}

	public AS getSpimData() { return data; }

	public void addListener( final SelectedViewDescriptionListener listener )
	{
		this.listeners.add( listener );
		
		// update it with the currently selected row
		listener.seletedViewDescription( tableModel.getElements().get( table.getSelectedRow() ) );
	}

	public boolean removeListener( final SelectedViewDescriptionListener listener )
	{
		return this.listeners.remove( listener );
	}
	
	public ArrayList< SelectedViewDescriptionListener > getListeners() { return listeners; }
	
	public void initComponent()
	{
		tableModel = new ViewSetupTableModel< AS >( data );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		table.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		
		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );
		
		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setCellRenderer( centerRenderer );
		
		// add listener to which row is selected
		table.getSelectionModel().addListSelectionListener(
			new ListSelectionListener() {
				@Override
				public void valueChanged( final ListSelectionEvent arg0 )
				{
					final int row = ((DefaultListSelectionModel)(arg0.getSource())).getAnchorSelectionIndex();
					
					if ( arg0.getValueIsAdjusting() && row >= 0 && row < tableModel.getRowCount() )
					{
						// not using an iterator allows that listeners can close the frame and remove all listeners while they are called
						for ( int i = 0; i < listeners.size(); ++i )
							listeners.get( i ).seletedViewDescription( tableModel.getElements().get( row ) );
					}
				}
			});

		// check out if the user clicked on the column header and potentially sorting by that
		table.getTableHeader().addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent mouseEvent)
			{
				int index = table.convertColumnIndexToModel(table.columnAtPoint(mouseEvent.getPoint()));
				if (index >= 0)
				{
					int row = table.getSelectedRow();
					tableModel.sortByColumn( index );
					table.clearSelection();
					table.getSelectionModel().setSelectionInterval( row, row );
				}
			};
		});
		
		table.setPreferredScrollableViewportSize( new Dimension( 750, 300 ) );
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( 20 );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( 15 );

		this.setLayout( new BorderLayout() );
		this.add( new JLabel( "XML: " + xml ), BorderLayout.NORTH );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );
		
		table.getSelectionModel().setSelectionInterval( 0, 0 );

		addPopupMenu( table );
	}

	public void saveXML()
	{
		try
		{
			io.save( data, xml );
			IOFunctions.println( "Saved XML '" + xml + "'." );
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "Failed to save XML '" + xml + "': " + e );
			e.printStackTrace();
		}
	}

	protected void addPopupMenu( final JTable table )
	{
		final JPopupMenu popupMenu = new JPopupMenu();
		final JMenuItem saveItem = new JMenuItem( "Save XML" );

		saveItem.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				saveXML();
			}
		});

		popupMenu.add( saveItem );
		table.setComponentPopupMenu( popupMenu );
	}
}