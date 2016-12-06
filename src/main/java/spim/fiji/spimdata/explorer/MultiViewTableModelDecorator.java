package spim.fiji.spimdata.explorer;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.event.TableModelListener;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class MultiViewTableModelDecorator < AS extends AbstractSpimData< ? > > implements ISpimDataTableModel< AS >
{
	
	private ISpimDataTableModel<AS> decorated;
	final ArrayList< String > columnNames;
	
	final int registrationColumn, interestPointsColumn;
	final ViewRegistrations viewRegistrations;
	final ViewInterestPoints viewInterestPoints;
	
	public MultiViewTableModelDecorator(ISpimDataTableModel<AS> decorated) {
		this.decorated = decorated;
		this.columnNames = new ArrayList<>();
		
		columnNames.add( "#Registrations" );

		registrationColumn = decorated.getColumnCount() + columnNames.size();
		viewRegistrations = decorated.getPanel().getSpimData().getViewRegistrations();

		if ( SpimData2.class.isInstance( decorated.getPanel().getSpimData() ) )
		{
			final SpimData2 data2 = (SpimData2)decorated.getPanel().getSpimData();
			columnNames.add( "#InterestPoints" );

			interestPointsColumn = decorated.getColumnCount() + columnNames.size();
			viewInterestPoints = data2.getViewInterestPoints();
		}
		else
		{
			viewInterestPoints = null;
			interestPointsColumn = -1;
		}
	}
	
	@Override
	public int getRowCount() {
		return decorated.getRowCount();
	}

	@Override
	public int getColumnCount() {
		// TODO implement for real
		return decorated.getColumnCount() + columnNames.size();
	}

	@Override
	public String getColumnName(int columnIndex) {
		// TODO Auto-generated method stub
		if (columnIndex < decorated.getColumnCount())
			return decorated.getColumnName(columnIndex);
		else
			return columnNames.get( columnIndex - decorated.getColumnCount() );
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		if (columnIndex < decorated.getColumnCount())
			return decorated.getColumnClass(columnIndex);
		else
			return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		
		// pass on to decorated
		if (columnIndex < decorated.getColumnCount())
			return decorated.getValueAt(rowIndex, columnIndex);
		
		final BasicViewDescription< ? extends BasicViewSetup > vd = getElements().get( rowIndex ).iterator().next();
		
		if (vd.isPresent())
		{
			if ( columnIndex == registrationColumn )			
					return this.viewRegistrations.getViewRegistration( vd ).getTransformList().size();
			else if ( columnIndex == interestPointsColumn && viewInterestPoints != null )
				return viewInterestPoints.getViewInterestPointLists( vd ).getHashMap().keySet().size();
		}
		// TODO: handle this nicely for grouped views
		return "missing";
		
	}

	@Override
	public void addTableModelListener(TableModelListener l) {decorated.addTableModelListener(l);}

	@Override
	public void removeTableModelListener(TableModelListener l) {decorated.removeTableModelListener(l);}

	@Override
	public void clearSortingFactors() {decorated.clearSortingFactors();}

	@Override
	public void addSortingFactor(Class<? extends Entity> factor) {decorated.addSortingFactor(factor);}

	@Override
	public void clearGroupingFactors() {decorated.clearGroupingFactors();}

	@Override
	public void addGroupingFactor(Class<? extends Entity> factor) {decorated.addGroupingFactor(factor);}

	@Override
	public void clearFilters() {decorated.clearFilters();}

	@Override
	public void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances) {decorated.addFilter(cl, instances);}

	@Override
	public List<List<BasicViewDescription<?>>> getElements() { return decorated.getElements(); }

	@Override
	public void sortByColumn(int column) {
		if (column < decorated.getColumnCount())
			decorated.sortByColumn(column);		
	}

	@Override
	public ExplorerWindow<AS, ?> getPanel() { return decorated.getPanel(); }

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex)
	{
		decorated.setValueAt( aValue, rowIndex, columnIndex );		
	}

	@Override
	public int getSpecialColumn(ISpimDataTableModel.SpecialColumnType type)
	{
		if (type == SpecialColumnType.INTEREST_POINT_COLUMN)
			return interestPointsColumn;
		else if (type == SpecialColumnType.VIEW_REGISTRATION_COLUMN)
			return registrationColumn;
		else
			return -1;
	}

	@Override
	public void setColumnClasses(List< Class< ? extends Entity > > columnClasses)
	{
		decorated.setColumnClasses( columnClasses );
		
	}

	@Override
	public Set< Class< ? extends Entity > > getGroupingFactors(){return decorated.getGroupingFactors();}


}
