package spim.fiji.spimdata.explorer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
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
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.SpimDataWrapper;
import spim.fiji.spimdata.explorer.popup.ApplyTransformationPopup;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.explorer.popup.DetectInterestPointsPopup;
import spim.fiji.spimdata.explorer.popup.DisplayViewPopup;
import spim.fiji.spimdata.explorer.popup.FusionPopup;
import spim.fiji.spimdata.explorer.popup.InterestPointsExplorerPopup;
import spim.fiji.spimdata.explorer.popup.LabelPopUp;
import spim.fiji.spimdata.explorer.popup.RegisterInterestPointsPopup;
import spim.fiji.spimdata.explorer.popup.RegistrationExplorerPopup;
import spim.fiji.spimdata.explorer.popup.RemoveDetectionsPopup;
import spim.fiji.spimdata.explorer.popup.ResavePopup;
import spim.fiji.spimdata.explorer.popup.Separator;
import spim.fiji.spimdata.explorer.popup.SpecifyCalibrationPopup;
import spim.fiji.spimdata.explorer.popup.ViewExplorerSetable;
import spim.fiji.spimdata.explorer.popup.VisualizeDetectionsPopup;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import bdv.BigDataViewer;
import bdv.img.hdf5.Hdf5ImageLoader;

public class ViewSetupExplorerPanel< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > > extends JPanel
{
	final static ArrayList< ViewExplorerSetable > staticPopups = new ArrayList< ViewExplorerSetable >();

	static
	{
		IOFunctions.printIJLog = true;
		staticPopups.add( new LabelPopUp( " Displaying" ) );
		staticPopups.add( new BDVPopup() );
		staticPopups.add( new DisplayViewPopup() );
		staticPopups.add( new Separator() );

		staticPopups.add( new LabelPopUp( " Processing" ) );
		staticPopups.add( new DetectInterestPointsPopup() );
		staticPopups.add( new RegisterInterestPointsPopup() );
		staticPopups.add( new FusionPopup() );
		staticPopups.add( new Separator() );

		staticPopups.add( new LabelPopUp( " Calibration/Transformations" ) );
		staticPopups.add( new RegistrationExplorerPopup() );
		staticPopups.add( new SpecifyCalibrationPopup() );
		staticPopups.add( new ApplyTransformationPopup() );
		staticPopups.add( new Separator() );

		staticPopups.add( new LabelPopUp( " Interest Points" ) );
		staticPopups.add( new InterestPointsExplorerPopup() );
		staticPopups.add( new VisualizeDetectionsPopup() );
		staticPopups.add( new RemoveDetectionsPopup() );
		staticPopups.add( new Separator() );

		staticPopups.add( new LabelPopUp( " Modifications" ) );
		staticPopups.add( new ResavePopup() );
	}

	private static final long serialVersionUID = -3767947754096099774L;
	
	protected JTable table;
	protected ViewSetupTableModel< AS > tableModel;
	protected ArrayList< SelectedViewDescriptionListener< AS > > listeners;
	protected AS data;
	protected ViewSetupExplorer< AS, X > explorer;
	final String xml;
	final X io;
	final boolean isMac;

	final protected HashSet< BasicViewDescription< ? extends BasicViewSetup > > selectedRows;

	public ViewSetupExplorerPanel( final ViewSetupExplorer< AS, X > explorer, final AS data, final String xml, final X io )
	{
		this.explorer = explorer;
		this.listeners = new ArrayList< SelectedViewDescriptionListener< AS > >();
		this.data = data;
		this.xml = xml.replace( "\\", "/" ).replace( "//", "/" ).replace( "/./", "/" );
		this.io = io;
		this.isMac = System.getProperty( "os.name" ).toLowerCase().contains( "mac" );
		this.selectedRows = new HashSet< BasicViewDescription< ? extends BasicViewSetup > >();

		initComponent();

		if ( Hdf5ImageLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) )
			for ( final ViewExplorerSetable s : staticPopups )
				if ( BDVPopup.class.isInstance( s ) )
					((BDVPopup)s).bdv = new BigDataViewer( new SpimDataWrapper( getSpimData() ), xml(), null );
	}

	public BDVPopup bdvPopup()
	{
		for ( final ViewExplorerSetable s : staticPopups )
			if ( BDVPopup.class.isInstance( s ) )
				return ((BDVPopup)s);

		return null;
	}
	public ViewSetupTableModel< AS > getTableModel() { return tableModel; }
	public AS getSpimData() { return data; }
	public String xml() { return xml; }
	public X io() { return io; }
	public ViewSetupExplorer< AS, X > explorer() { return explorer; }

	@SuppressWarnings("unchecked")
	public void setSpimData( final Object data ) { this.data = (AS)data; }

	public void updateContent()
	{
		this.getTableModel().fireTableDataChanged();
		for ( final SelectedViewDescriptionListener< AS > l : listeners )
			l.updateContent( this.data );
	}

	public List< BasicViewDescription< ? extends BasicViewSetup > > selectedRows()
	{
		final ArrayList< BasicViewDescription< ? extends BasicViewSetup > > list = new ArrayList< BasicViewDescription< ? extends BasicViewSetup > >();
		list.addAll( selectedRows );
		Collections.sort( list );
		return list;
	}

	public List< ViewId > selectedRowsViewId()
	{
		final ArrayList< ViewId > list = new ArrayList< ViewId >();
		list.addAll( selectedRows );
		Collections.sort( list );
		return list;
	}

	public void addListener( final SelectedViewDescriptionListener< AS > listener )
	{
		this.listeners.add( listener );
		
		// update it with the currently selected row
		listener.seletedViewDescription( tableModel.getElements().get( table.getSelectedRow() ) );
	}

	public boolean removeListener( final SelectedViewDescriptionListener< AS > listener )
	{
		return this.listeners.remove( listener );
	}
	
	public ArrayList< SelectedViewDescriptionListener< AS > > getListeners() { return listeners; }
	
	public void initComponent()
	{
		tableModel = new ViewSetupTableModel< AS >( this );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		
		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );
		
		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setCellRenderer( centerRenderer );

		// add listener to which row is selected
		table.getSelectionModel().addListSelectionListener( getSelectionListener() );

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

		if ( isMac )
			addAppleA();

		table.setPreferredScrollableViewportSize( new Dimension( 750, 300 ) );
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( 20 );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( 15 );
		table.getColumnModel().getColumn( tableModel.registrationColumn() ).setPreferredWidth( 25 );

		if ( tableModel.interestPointsColumn() >= 0 )
			table.getColumnModel().getColumn( tableModel.interestPointsColumn() ).setPreferredWidth( 30 );

		this.setLayout( new BorderLayout() );

		final JButton save = new JButton( "Save" );
		save.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				if ( save.isEnabled() )
					saveXML();
			}
		});

		final JButton info = new JButton( "Info" );
		info.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				if ( info.isEnabled() )
					showInfoBox();
			}
		});

		final JPanel buttons = new JPanel( new BorderLayout() );
		buttons.add( info, BorderLayout.WEST );
		buttons.add( save, BorderLayout.EAST );

		final JPanel header = new JPanel( new BorderLayout() );
		header.add( new JLabel( "XML: " + xml ), BorderLayout.WEST );
		header.add( buttons, BorderLayout.EAST );
		this.add( header, BorderLayout.NORTH );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );

		table.getSelectionModel().setSelectionInterval( 0, 0 );

		addPopupMenu( table );
	}

	protected ListSelectionListener getSelectionListener()
	{
		return new ListSelectionListener()
		{
			int lastRow = -1;

			@Override
			public void valueChanged( final ListSelectionEvent arg0 )
			{
				if ( table.getSelectedRowCount() != 1 )
				{
					lastRow = -1;

					for ( int i = 0; i < listeners.size(); ++i )
						listeners.get( i ).seletedViewDescription( null );

					selectedRows.clear();

					for ( final int row : table.getSelectedRows() )
						selectedRows.add( tableModel.getElements().get( row ) );
				}
				else
				{
					final int row = table.getSelectedRow();

					if ( ( row != lastRow ) && row >= 0 && row < tableModel.getRowCount() )
					{
						lastRow = row;

						// not using an iterator allows that listeners can close the frame and remove all listeners while they are called
						final BasicViewDescription< ? extends BasicViewSetup > vd = tableModel.getElements().get( row );
						for ( int i = 0; i < listeners.size(); ++i )
							listeners.get( i ).seletedViewDescription( vd );

						selectedRows.clear();
						selectedRows.add(vd );
					}
				}
			}
		};
	}

	public HashSet< BasicViewDescription< ? extends BasicViewSetup > > getSelectedRows() { return selectedRows; }

	public void showInfoBox()
	{
		new ViewSetupExplorerInfoBox< AS >( data, xml );
	}

	public void saveXML()
	{
		try
		{
			io.save( data, xml );

			for ( final SelectedViewDescriptionListener< AS > l : listeners )
				l.save();

			if ( SpimData2.class.isInstance( data ) )
			{
				final ViewInterestPoints vip = ( (SpimData2)data ).getViewInterestPoints();
				
				for ( final ViewInterestPointLists vipl : vip.getViewInterestPoints().values() )
				{
					for ( final String label : vipl.getHashMap().keySet() )
					{
						final InterestPointList ipl = vipl.getInterestPointList( label );
	
						if ( ipl.getInterestPoints() == null )
							ipl.loadInterestPoints();
						
						ipl.saveInterestPoints();
	
						if ( ipl.getCorrespondingInterestPoints() == null )
							ipl.loadCorrespondingInterestPoints();
	
						ipl.saveCorrespondingInterestPoints();
					}
				}
			}

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

		for ( final ViewExplorerSetable item : staticPopups )
			popupMenu.add( item.setViewExplorer( this ) );

		table.setComponentPopupMenu( popupMenu );
	}

	protected void addAppleA()
	{
		table.addKeyListener( new KeyListener()
		{
			boolean appleKeyDown = false;

			@Override
			public void keyTyped( KeyEvent arg0 )
			{
				if ( appleKeyDown && arg0.getKeyChar() == 'a' )
					table.selectAll();
			}

			@Override
			public void keyReleased( KeyEvent arg0 )
			{
				if ( arg0.getKeyCode() == 157 )
					appleKeyDown = false;
			}

			@Override
			public void keyPressed(KeyEvent arg0)
			{
				if ( arg0.getKeyCode() == 157 )
					appleKeyDown = true;
			}
		});
	}
}