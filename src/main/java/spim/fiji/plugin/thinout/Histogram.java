/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package spim.fiji.plugin.thinout;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import ij.ImageJ;
import net.imglib2.util.ValuePair;

public class Histogram extends ApplicationFrame
{
	private static final long serialVersionUID = 1L;
	protected double min, max;

	public Histogram( final List< Double > values, final int numBins, final String title, final String units )
	{
		super( title );

		final IntervalXYDataset dataset = createDataset( values, numBins, title );
		final JFreeChart chart = createChart( dataset, title, units );
		final ChartPanel chartPanel = new ChartPanel( chart );
		chartPanel.addChartMouseListener( new MouseListenerValue( chartPanel, getMin() + ( getMax() - getMin() ) / 2 ));

		chartPanel.setPreferredSize( new Dimension( 600, 270 ) );
		setContentPane( chartPanel );
	}

	public void showHistogram()
	{
		this.pack();
		centerFrameOnScreen( this );
		this.setVisible( true );
	}

	public double getMin() { return min; }
	public double getMax() { return max; }

	public static ValuePair< Double, Double > getMinMax( final List< Double > data )
	{
		// compute min/max/size
		double min = data.get( 0 );
		double max = data.get( 0 );

		for ( final double v : data )
		{
			min = Math.min( min, v );
			max = Math.max( max, v );
		}

		return new ValuePair< >( min, max );
	}

	public static List< ValuePair< Double, Integer > > binData( final List< Double > data, final double min, final double max, final int numBins )
	{
		// avoid the one value that is exactly 100%
		final double size = max - min + 0.000001;

		// bin and count the entries
		final int[] bins = new int[ numBins ];

		for ( final double v : data )
			++bins[ (int)Math.floor( ( ( v - min ) / size ) * numBins ) ];

		// make the list of bins
		final ArrayList< ValuePair< Double, Integer > > hist = new ArrayList< >();

		final double binSize = size / numBins;
		for ( int bin = 0; bin < numBins; ++bin )
			hist.add( new ValuePair< >( min + binSize/2 + binSize * bin, bins[ bin ] ) );

		return hist;
	}

	protected IntervalXYDataset createDataset( final List< Double > values, final int numBins, final String title )
	{
		final XYSeries series = new XYSeries( title );

		final ValuePair< Double, Double > minmax = getMinMax( values );
		this.min = minmax.getA();
		this.max = minmax.getB();

		final List< ValuePair< Double, Integer > > hist = binData( values, min, max, numBins );

		for ( final ValuePair< Double, Integer > pair : hist )
			series.add( pair.getA(), pair.getB() );

		final XYSeriesCollection dataset = new XYSeriesCollection( series );
		dataset.setAutoWidth( true );

		return dataset;
	}

	protected JFreeChart createChart( final IntervalXYDataset dataset, final String title, final String units )
	{
		final JFreeChart chart = ChartFactory.createXYBarChart(
			title,
			"Distance [" + units + "]",
			false,
			"Count",
			dataset,
			PlotOrientation.VERTICAL,
			false, // legend
			false,
			false );

		final NumberAxis range = (NumberAxis) chart.getXYPlot().getDomainAxis();
		range.setRange( getMin(), getMax() );

		final XYPlot plot = chart.getXYPlot();
		final XYBarRenderer renderer = (XYBarRenderer) plot.getRenderer();

		renderer.setSeriesPaint( 0, Color.red );
		renderer.setDrawBarOutline( true );
		renderer.setSeriesOutlinePaint( 0, Color.black );
		renderer.setBarPainter( new StandardXYBarPainter() );

		return chart;
	}

	@Override
	public void windowClosing( final WindowEvent evt )
	{
		if( evt.getWindow() == this )
			dispose();
	}

	public static void main( final String[] args )
	{
		new ImageJ();

		final List< Double > values = new ArrayList< >();
		final Random rnd = new Random();

		for ( int i = 0; i < 10000; ++i )
			values.add( rnd.nextGaussian() );

		final Histogram demo = new Histogram( values, 100, "Histogram for ...", "pixels" );
		demo.pack();
		centerFrameOnScreen( demo );
		demo.setVisible( true );
	}

	/**
	 * Positions the specified frame in the middle of the screen.
	 *
	 * @param frame
	 *            the frame to be centered on the screen.
	 */
	private static void centerFrameOnScreen( final Window frame )
	{
		positionFrameOnScreen( frame, 0.5, 0.5 );
	}

	/**
	 * Positions the specified frame at a relative position in the screen, where
	 * 50% is considered to be the center of the screen.
	 *
	 * @param frame
	 *            the frame.
	 * @param horizontalPercent
	 *            the relative horizontal position of the frame (0.0 to 1.0,
	 *            where 0.5 is the center of the screen).
	 * @param verticalPercent
	 *            the relative vertical position of the frame (0.0 to 1.0, where
	 *            0.5 is the center of the screen).
	 */
	private static void positionFrameOnScreen( final Window frame, final double horizontalPercent, final double verticalPercent )
	{

		final Rectangle s = frame.getGraphicsConfiguration().getBounds();
		final Dimension f = frame.getSize();
		final int w = Math.max( s.width - f.width, 0 );
		final int h = Math.max( s.height - f.height, 0 );
		final int x = ( int ) ( horizontalPercent * w ) + s.x;
		final int y = ( int ) ( verticalPercent * h ) + s.y;
		frame.setBounds( x, y, f.width, f.height );

	}
}
