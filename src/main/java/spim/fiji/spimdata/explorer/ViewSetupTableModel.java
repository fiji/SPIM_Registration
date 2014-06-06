package spim.fiji.spimdata.explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.table.AbstractTableModel;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;

public class ViewSetupTableModel extends AbstractTableModel
{
	private static final long serialVersionUID = -6526338840427674269L;
	
	final ArrayList< BasicViewDescription< ? extends BasicViewSetup > > elements = new ArrayList< BasicViewDescription< ? extends BasicViewSetup > >();
	final ArrayList< String > columnNames;
	
	public ViewSetupTableModel( final SpimData data )
	{
		columnNames = new ArrayList< String >();
		columnNames.add( "Timepoint" );
		columnNames.add( "View Id" );
		columnNames.addAll( data.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getAttributes().keySet() );

		for ( final TimePoint t : data.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
			for ( final ViewSetup v : data.getSequenceDescription().getViewSetupsOrdered() )
			{
				final ViewId viewId = new ViewId( t.getId(), v.getId() );
				final ViewDescription viewDesc = data.getSequenceDescription().getViewDescription( viewId );

				if ( viewDesc.isPresent() )
					elements.add( viewDesc );
			}
	}

	public void sortByColumn( final int column )
	{
		Collections.sort( elements, new Comparator< BasicViewDescription< ? extends BasicViewSetup > >()
		{
			@Override
			public int compare(
					BasicViewDescription<? extends BasicViewSetup> arg0,
					BasicViewDescription<? extends BasicViewSetup> arg1)
			{
				if ( column == 0 )
				{
					final int diff = arg0.getTimePointId() - arg1.getTimePointId();
					return diff == 0 ? arg0.getViewSetupId() - arg1.getViewSetupId() : diff;
				}
				else if ( column == 1 )
				{
					final int diff = arg0.getViewSetupId() - arg1.getViewSetupId();
					return diff == 0 ? arg0.getTimePointId() - arg1.getTimePointId() : diff;
				}
				else
				{
					final int diff1 = arg0.getViewSetup().getAttributes().get( columnNames.get( column ) ).getId() - arg1.getViewSetup().getAttributes().get( columnNames.get( column ) ).getId();
					final int diff2 = arg0.getViewSetupId() - arg1.getViewSetupId();
					
					return diff1 == 0 ? ( diff2 == 0 ? arg0.getTimePointId() - arg1.getTimePointId() : diff2 ) : diff1;
				}
			}
		});
		
		fireTableDataChanged();
	}
	
	public ArrayList< BasicViewDescription< ? extends BasicViewSetup > > getElements() { return elements; }
	
	@Override
	public int getColumnCount() { return columnNames.size(); }
	
	@Override
	public int getRowCount() { return elements.size(); }

	@Override
	public boolean isCellEditable( final int row, final int column )
	{
		return false;
	}

	public void setValueAt( final Object value, final int row, final int column )
	{
		// do something ...
		fireTableCellUpdated( row, column );
	}
	
	@Override
	public Object getValueAt( final int row, final int column )
	{
		final BasicViewDescription< ? extends BasicViewSetup > vd = elements.get( row );

		if ( column == 0 )
			return vd.getTimePoint().getId();
		else if ( column == 1 )
			return vd.getViewSetupId();
		else
		{
			final Entity e = vd.getViewSetup().getAttributes().get( columnNames.get( column ) );

			if ( e instanceof NamedEntity )
				return ((NamedEntity)e).getName() + " (id = " + e.getId() + ")";
			else
				return e.getId() + " (no name available)";
		}
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnNames.get( column );
	}
}
