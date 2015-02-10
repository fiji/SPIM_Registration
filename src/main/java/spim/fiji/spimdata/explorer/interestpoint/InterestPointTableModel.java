package spim.fiji.spimdata.explorer.interestpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class InterestPointTableModel extends AbstractTableModel
{
	private static final long serialVersionUID = -1263388435427674269L;
	
	final ViewInterestPoints viewInterestPoints;
	final ArrayList< String > columnNames;
	
	BasicViewDescription< ? > currentVD;
	
	public InterestPointTableModel( final ViewInterestPoints viewInterestPoints )
	{
		this.columnNames = new ArrayList< String >();

		this.columnNames.add( "Interest Point Label" );
		this.columnNames.add( "#Detections" );
		this.columnNames.add( "#Corresponding" );
		this.columnNames.add( "#Total Corresponding" );
		this.columnNames.add( "Parameters" );

		this.viewInterestPoints = viewInterestPoints;
		this.currentVD = null;
	}
	
	protected ViewInterestPoints getViewInterestPoints() { return viewInterestPoints; }
	protected BasicViewDescription< ? > getCurrentViewDescription() { return currentVD; } 
	
	protected void updateViewDescription( final BasicViewDescription< ? > vd )
	{
		this.currentVD = vd;
		
		// update everything
		fireTableDataChanged();
	}

	@Override
	public int getColumnCount() { return columnNames.size(); }
	
	@Override
	public int getRowCount()
	{ 
		if ( currentVD == null )
			return 1;
		else
			return Math.max( 1, viewInterestPoints.getViewInterestPointLists( currentVD ).getHashMap().keySet().size() );
	}

	@Override
	public boolean isCellEditable( final int row, final int column )
	{
		if ( column == 4 )
			return true;
		else
			return false;
	}

	@Override
	public void setValueAt( final Object value, final int row, final int column )
	{
		if ( column == 4 )
		{
			final String label = label( currentVD, row );

			viewInterestPoints.getViewInterestPointLists( currentVD ).getInterestPointList( label ).setParameters( value.toString() );

			// do something ...
			fireTableCellUpdated( row, column );
		}
	}

	protected String label( final BasicViewDescription< ? >vd, final int row )
	{
		final ArrayList< String > labels = new ArrayList< String >();
		labels.addAll( viewInterestPoints.getViewInterestPointLists( vd ).getHashMap().keySet() );
		Collections.sort( labels );
		return labels.get( row );
	}

	@Override
	public Object getValueAt( final int row, final int column )
	{
		if ( currentVD == null )
			return column == 0 ? "No View Description selected" : "";

		final ViewInterestPointLists vip = viewInterestPoints.getViewInterestPointLists( currentVD );
		final HashMap< String, InterestPointList > hash = vip.getHashMap();

		if ( hash.keySet().size() == 0 )
		{
			return column == 0 ? "No interest points segmented" : "";
		}
		else
		{
			final String label = label( currentVD, row );

			if ( column == 0 )
				return label;
			else if ( column == 1 )
				return numDetections( hash.get( label ) );
			else if ( column == 4 )
				return hash.get( label ).getParameters();
			else
				return -1;//TODO
		}
	}

	protected int numDetections( final InterestPointList ipList )
	{
		final List< InterestPoint > list = ipList.getInterestPoints();

		if ( list == null || list.size() == 0 )
			if ( !ipList.loadInterestPoints() )
				return -1;

		return list.size();
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnNames.get( column );
	}
}
