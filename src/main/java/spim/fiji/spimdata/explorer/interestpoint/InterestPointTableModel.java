package spim.fiji.spimdata.explorer.interestpoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.table.AbstractTableModel;

import bdv.BigDataViewer;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RealLocalizable;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.explorer.interestpoint.InterestPointOverlay.InterestPointSource;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class InterestPointTableModel extends AbstractTableModel implements InterestPointSource
{
	private static final long serialVersionUID = -1263388435427674269L;
	
	ViewInterestPoints viewInterestPoints;
	final ArrayList< String > columnNames;

	BasicViewDescription< ? > currentVD;
	final InterestPointExplorerPanel panel;

	private int selectedRow = -1;
	private int selectedCol = -1;

	final ArrayList< InterestPointSource > interestPointSources;
	volatile InterestPointOverlay interestPointOverlay = null;
	Collection< ? extends RealLocalizable > points = new ArrayList< RealLocalizable >();

	public InterestPointTableModel( final ViewInterestPoints viewInterestPoints, final InterestPointExplorerPanel panel )
	{
		this.columnNames = new ArrayList< String >();

		this.columnNames.add( "Interest Point Label" );
		this.columnNames.add( "#Detections" );
		this.columnNames.add( "#Corresponding" );
		this.columnNames.add( "#Correspondences" );
		this.columnNames.add( "Parameters" );

		this.viewInterestPoints = viewInterestPoints;
		this.currentVD = null;
		this.panel = panel;

		this.interestPointSources = new ArrayList< InterestPointSource >();
		this.interestPointSources.add( this );

	}

	protected void update( final ViewInterestPoints viewInterestPoints ) { this.viewInterestPoints = viewInterestPoints; }
	protected ViewInterestPoints getViewInterestPoints() { return viewInterestPoints; }
	protected BasicViewDescription< ? > getCurrentViewDescription() { return currentVD; } 
	
	protected void updateViewDescription( final BasicViewDescription< ? > vd, final boolean isFirst )
	{
		this.currentVD = vd;

		// update everything
		fireTableDataChanged();

		if ( isFirst )
		{
			// by default show the detections of the first entry if available
			setSelected( 0, 1 );
		}
		else
		{
			setSelected( selectedRow, selectedCol );
		}
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
			final String label = label( viewInterestPoints, currentVD, row );

			viewInterestPoints.getViewInterestPointLists( currentVD ).getInterestPointList( label ).setParameters( value.toString() );

			// do something ...
			fireTableCellUpdated( row, column );
		}
	}

	public static String label( final ViewInterestPoints viewInterestPoints, final BasicViewDescription< ? > vd, final int row )
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

		final HashMap< String, InterestPointList > hash = viewInterestPoints.getViewInterestPointLists( currentVD ).getHashMap();

		if ( hash.keySet().size() == 0 )
		{
			return column == 0 ? "No interest points segmented" : "";
		}
		else
		{
			final String label = label( viewInterestPoints, currentVD, row );

			if ( column == 0 )
				return label;
			else if ( column == 1 )
				return numDetections( viewInterestPoints, currentVD, label );
			else if ( column == 2 )
				return numCorresponding( viewInterestPoints, currentVD, label );
			else if ( column == 3 )
				return numCorrespondences( viewInterestPoints, currentVD, label );
			else if ( column == 4 )
				return hash.get( label ).getParameters();
			else
				return -1;
		}
	}

	protected int numCorresponding( final ViewInterestPoints vip, final ViewId v, final String label )
	{
		final HashSet< Integer > cips = new HashSet< Integer >();

		for ( final CorrespondingInterestPoints c : panel.getCorrespondingInterestPoints( vip, v, label ) )
			cips.add( c.getDetectionId() );

		return cips.size();
	}

	protected int numCorrespondences( final ViewInterestPoints vip, final ViewId v, final String label )
	{
		return panel.getCorrespondingInterestPoints( vip, v, label ).size();
	}

	protected int numDetections( final ViewInterestPoints vip, final ViewId v, final String label )
	{
		return panel.getInterestPoints( vip, v, label ).size();
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnNames.get( column );
	}

	public boolean getState( final int row, final int column )
	{
		if ( row == selectedRow && column == selectedCol )
			return true;
		else
			return false;
	}

	public void setSelected( final int row, final int col )
	{
		if ( currentVD != null && BDVPopup.bdvRunning() && row >= 0 && row < getRowCount() && col >= 1 && col <= 2  )
		{
			this.selectedRow = row;
			this.selectedCol = col;

			final String label = label( viewInterestPoints, currentVD, row );

			if ( col == 1 )
			{
				points = panel.getInterestPoints( viewInterestPoints, currentVD, label );
			}
			else //if ( col == 2 )
			{
				final HashMap< Integer, InterestPoint > map = new HashMap< Integer, InterestPoint >();
				
				for ( final InterestPoint ip : panel.getInterestPoints( viewInterestPoints, currentVD, label ) )
					map.put( ip.getId(), ip );

				final ArrayList< InterestPoint > tmp = new ArrayList< InterestPoint >();

				for ( final CorrespondingInterestPoints ip : panel.getCorrespondingInterestPoints( viewInterestPoints, currentVD, label ) )
					tmp.add( map.get( ip.getDetectionId() ) );

				points = tmp;
			}

			if ( interestPointOverlay == null )
			{
				final BigDataViewer bdv = ViewSetupExplorerPanel.bdvPopup().bdv;
				interestPointOverlay = new InterestPointOverlay( bdv.getViewer(), interestPointSources );
				bdv.getViewer().addRenderTransformListener( interestPointOverlay );
				bdv.getViewer().getDisplay().addOverlayRenderer( interestPointOverlay );
				ViewSetupExplorerPanel.bdvPopup().updateBDV();
			}
		}
		else
		{
			this.selectedRow = this.selectedCol = -1;
			this.points = new ArrayList< RealLocalizable >();
		}

		if ( BDVPopup.bdvRunning() )
			ViewSetupExplorerPanel.bdvPopup().updateBDV();
	}

	public int getSelectedRow() { return selectedRow; }
	public int getSelectedCol() { return selectedCol; }

	@Override
	public Collection< ? extends RealLocalizable > getLocalCoordinates( final int timepointIndex )
	{
		if ( currentVD != null && timepointIndex == ViewSetupExplorerPanel.getBDVTimePointIndex( currentVD.getTimePoint(), panel.viewSetupExplorer.getSpimData() ) )
			return points;
		else
			return new ArrayList< RealLocalizable >();
	}

	@Override
	public void getLocalToGlobalTransform( final int timepointIndex, final AffineTransform3D transform )
	{
		if ( currentVD != null )
		{
			final ViewRegistration vr = panel.viewSetupExplorer.getSpimData().getViewRegistrations().getViewRegistration( currentVD );
			vr.updateModel();
			transform.set( vr.getModel() );
		}
	}
}
