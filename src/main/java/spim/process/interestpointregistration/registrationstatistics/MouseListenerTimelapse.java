package spim.process.interestpointregistration.registrationstatistics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.TextAnchor;

import spim.fiji.plugin.Interest_Point_Registration;

public class MouseListenerTimelapse implements ChartMouseListener
{
	final ChartPanel panel;
	ValueMarker valueMarker;
	boolean markerShown = false;
	int referenceTimePoint;
	final boolean enableReferenceTimePoint;
	final TimePoints timepoints;

	// update the location of the last right click and the filename to open
	final ArrayList< SelectTimepointEntry > updateList = new ArrayList< SelectTimepointEntry >();
	
	MouseListenerTimelapse( final TimePoints timepoints, final ChartPanel panel )
	{
		this( timepoints, panel, -1, false );
	}

	MouseListenerTimelapse( final TimePoints timepoints, final ChartPanel panel, final boolean enableReferenceTimePoint )
	{
		this( timepoints, panel, -1, enableReferenceTimePoint );
	}

	MouseListenerTimelapse( final TimePoints timepoints, final ChartPanel panel, final int referenceTimePoint, final boolean enableReferenceTimePoint )
	{
		this.timepoints = timepoints;
		this.panel = panel;
		this.referenceTimePoint = referenceTimePoint;
		this.enableReferenceTimePoint = enableReferenceTimePoint;
		
		if ( enableReferenceTimePoint || referenceTimePoint != -1 ) // at least show it if it is not -1
		{
			if ( timepoints != null )
				setReferenceTimepoint( timepoints, referenceTimePoint );

			valueMarker = makeMarker( referenceTimePoint );

			if ( referenceTimePoint >= 0 )
			{
				((XYPlot)panel.getChart().getPlot()).addDomainMarker( valueMarker );
				markerShown = true;
			}
		}
	}
	
	public int getReferenceTimePoint() { return referenceTimePoint; }
	
	protected ValueMarker makeMarker( final int timePoint )
	{
		final ValueMarker valueMarker = new ValueMarker( timePoint );
		valueMarker.setStroke( new BasicStroke ( 1.5f ) );
		valueMarker.setPaint( new Color( 0.0f, 93f/255f, 9f/255f ) );
		valueMarker.setLabel( " Reference\n Timepoint " + timePoint );
		valueMarker.setLabelAnchor(RectangleAnchor.BOTTOM );
		valueMarker.setLabelTextAnchor( TextAnchor.BOTTOM_LEFT );
		
		return valueMarker;
	}

	@Override
	public void chartMouseClicked( final ChartMouseEvent e )
	{
		// left mouse click
		if ( e.getTrigger().getButton() == MouseEvent.BUTTON1 && enableReferenceTimePoint )
		{
			int referenceTimePoint = getChartXLocation( e.getTrigger().getPoint(), panel );

			if ( timepoints != null )
			{
				if ( setReferenceTimepoint( timepoints, referenceTimePoint ) )
					this.referenceTimePoint = referenceTimePoint;
			}
			else
			{
				this.referenceTimePoint = referenceTimePoint;
			}

			valueMarker.setValue( this.referenceTimePoint );
			valueMarker.setLabel( " Reference\n Timepoint " + this.referenceTimePoint );
			
			if ( !markerShown )
			{
				((XYPlot) e.getChart().getPlot()).addDomainMarker( valueMarker );
				markerShown = true;
			}
		}
	}

	public static boolean setReferenceTimepoint( final TimePoints timepoints, final int referenceTimePoint )
	{
		final TimePoint ref = timepoints.getTimePoints().get( referenceTimePoint );
		if ( ref != null )
		{
			final List< TimePoint > tps = timepoints.getTimePointsOrdered();
			for ( int tp = 0; tp < tps.size(); ++tp )
			{
				if ( tps.get( tp ).getId() == referenceTimePoint )
				{
					Interest_Point_Registration.defaultReferenceTimepointIndex = tp;
					return true;
				}
			}
		}

		return false;
	}

	public static int getChartXLocation( final Point point, final ChartPanel panel )
	{
		final Point2D p = panel.translateScreenToJava2D( point );
		final Rectangle2D plotArea = panel.getScreenDataArea();
		final XYPlot plot = (XYPlot) panel.getChart().getPlot();
		final double chartX = plot.getDomainAxis().java2DToValue( p.getX(), plotArea, plot.getDomainAxisEdge() );
		//final double chartY = plot.getRangeAxis().java2DToValue( p.getY(), plotArea, plot.getRangeAxisEdge() );

		return (int)Math.round( chartX );
	}

	@Override
	public void chartMouseMoved( ChartMouseEvent e )
	{
	}
}
