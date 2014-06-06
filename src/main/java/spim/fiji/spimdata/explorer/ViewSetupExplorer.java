package spim.fiji.spimdata.explorer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.swing.DefaultListSelectionModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;

import spim.fiji.plugin.LoadParseQueryXML;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;

public class ViewSetupExplorer extends JPanel
{
	private static final long serialVersionUID = -3767947754096099774L;
	
	protected JTable table;
	protected ViewSetupTableModel tableModel;
	protected ArrayList< SelectedViewDescriptionListener > listeners;
	
	public ViewSetupExplorer()
	{
		this.listeners = new ArrayList< SelectedViewDescriptionListener >();
		initComponent();
	}
	
	public void addListener( final SelectedViewDescriptionListener listener )
	{
		this.listeners.add( listener );
		
		// update it with the currently selected row
		listener.seletedViewDescription( tableModel.getElements().get( table.getSelectedRow() ) );
	}
	
	public ArrayList< SelectedViewDescriptionListener > getListeners() { return listeners; }
	
	public void initComponent()
	{
		final XMLParseResult result = new LoadParseQueryXML().queryXML( "ViewSetup Explorer", "", false, false, false, false );
		
		if ( result == null )
			return;
		
		tableModel = new ViewSetupTableModel( result.getData() );
		tableModel.addTableModelListener( new InteractiveTableModelListener() );

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
						for ( final SelectedViewDescriptionListener listener : listeners )
							listener.seletedViewDescription( tableModel.getElements().get( row ) );
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
		
		table.setPreferredScrollableViewportSize( new Dimension( 500, 300 ) );
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( 20 );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( 15 );

		setLayout( new BorderLayout() );
		add( new JScrollPane( table ), BorderLayout.CENTER );
		
		table.getSelectionModel().setSelectionInterval( 0, 0 );
	}
	
	public class InteractiveTableModelListener implements TableModelListener
	{
		@Override
		public void tableChanged( final TableModelEvent evt )
		{
			System.out.println( evt );
		}
	}
	
	public static void main( final String[] args ) throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException
	{
		UIManager.setLookAndFeel( UIManager.getCrossPlatformLookAndFeelClassName() );

		JFrame frame = new JFrame( "ViewSetup Explorer" );
		frame.addWindowListener( new WindowAdapter()
		{
			public void windowClosing( final WindowEvent evt ) { return; }
		});
		
		final ViewSetupExplorer explorer = new ViewSetupExplorer();

		frame.getContentPane().add( explorer );
		frame.pack();
		frame.setVisible( true );

		// adding a listener
		explorer.addListener( new SimpleListener() );
	}
}