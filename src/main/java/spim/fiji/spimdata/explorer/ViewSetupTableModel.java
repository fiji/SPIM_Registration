package spim.fiji.spimdata.explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.table.AbstractTableModel;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class ViewSetupTableModel< AS extends AbstractSpimData< ? > > extends AbstractTableModel
{
	private static final long serialVersionUID = -6526338840427674269L;

	protected ArrayList< BasicViewDescription< ? extends BasicViewSetup > > elements = null;
	
	final ViewSetupExplorerPanel< AS, ? > panel;
	final ArrayList< String > columnNames;

	final int registrationColumn, interestPointsColumn;
	final ViewRegistrations viewRegistrations;
	final ViewInterestPoints viewInterestPoints;

	public int registrationColumn() { return registrationColumn; }
	public int interestPointsColumn() { return interestPointsColumn; }

	public ViewSetupTableModel( final ViewSetupExplorerPanel< AS, ? > panel )
	{
		this.panel = panel;
		columnNames = new ArrayList< String >();
		columnNames.add( "Timepoint" );
		columnNames.add( "View Id" );
		columnNames.addAll( panel.getSpimData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getAttributes().keySet() );
		columnNames.add( "#Registrations" );

		registrationColumn = columnNames.size() - 1;
		viewRegistrations = panel.getSpimData().getViewRegistrations();

		if ( SpimData2.class.isInstance( panel.getSpimData() ) )
		{
			final SpimData2 data2 = (SpimData2)panel.getSpimData();
			columnNames.add( "#InterestPoints" );

			interestPointsColumn = columnNames.size() - 1;
			viewInterestPoints = data2.getViewInterestPoints();
		}
		else
		{
			viewInterestPoints = null;
			interestPointsColumn = -1;
		}
	}

	protected ArrayList< BasicViewDescription< ? extends BasicViewSetup > > elements()
	{
		final ArrayList< BasicViewDescription< ? extends BasicViewSetup > > elementsNew = new ArrayList< BasicViewDescription< ? extends BasicViewSetup > >();

		for ( final TimePoint t : panel.getSpimData().getSequenceDescription().getTimePoints().getTimePointsOrdered() )
			for ( final BasicViewSetup v : panel.getSpimData().getSequenceDescription().getViewSetupsOrdered() )
			{
				final ViewId viewId = new ViewId( t.getId(), v.getId() );
				final BasicViewDescription< ? > viewDesc = panel.getSpimData().getSequenceDescription().getViewDescriptions().get( viewId );

				if ( viewDesc.isPresent() )
					elementsNew.add( viewDesc );
			}

		if ( this.elements == null || this.elements.size() != elementsNew.size() )
			this.elements = elementsNew;

		return elements;
	}

	public void sortByColumn( final int column )
	{
		Collections.sort( elements(), new Comparator< BasicViewDescription< ? extends BasicViewSetup > >()
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
				else if ( column == registrationColumn )
				{
					final int diff1 = viewRegistrations.getViewRegistration( arg0 ).getTransformList().size() - viewRegistrations.getViewRegistration( arg1 ).getTransformList().size();
					final int diff2 = arg0.getTimePointId() - arg1.getTimePointId();

					return diff1 == 0 ? ( diff2 == 0 ? arg0.getViewSetupId() - arg1.getViewSetupId() : diff2 ) : diff1;
				}
				else if ( column == interestPointsColumn && viewInterestPoints != null )
				{
					final int diff1 = viewInterestPoints.getViewInterestPointLists( arg0 ).getHashMap().keySet().size() - viewInterestPoints.getViewInterestPointLists( arg1 ).getHashMap().keySet().size();
					final int diff2 = arg0.getTimePointId() - arg1.getTimePointId();

					return diff1 == 0 ? ( diff2 == 0 ? arg0.getViewSetupId() - arg1.getViewSetupId() : diff2 ) : diff1;
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
	
	public ArrayList< BasicViewDescription< ? extends BasicViewSetup > > getElements() { return elements(); }

	@Override
	public int getColumnCount() { return columnNames.size(); }
	
	@Override
	public int getRowCount() { return elements().size(); }

	@Override
	public boolean isCellEditable( final int row, final int column )
	{
		return false;
	}

	@Override
	public Object getValueAt( final int row, final int column )
	{
		final BasicViewDescription< ? extends BasicViewSetup > vd = elements().get( row );

		if ( column == 0 )
			return vd.getTimePoint().getId();
		else if ( column == 1 )
			return vd.getViewSetupId();
		else if ( column == registrationColumn )
			return this.viewRegistrations.getViewRegistration( vd ).getTransformList().size();
		else if ( column == interestPointsColumn && viewInterestPoints != null )
			return viewInterestPoints.getViewInterestPointLists( vd ).getHashMap().keySet().size();
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
