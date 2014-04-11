package spim.fiji.plugin.fusion;

import java.util.ArrayList;

import spim.fiji.spimdata.SpimData2;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RealPositionable;

public abstract class BoundingBox implements Interval
{
	public static int staticDownsampling = 1;
	
	public static int minStatic[] = { 0, 0, 0 };
	public static int maxStatic[] = { 0, 0, 0 };

	/**
	 * which angles to process, set in queryParameters
	 */
	final ArrayList< Angle > anglesToProcess;

	/**
	 * which channels to process, set in queryParameters
	 */
	final ArrayList< Channel> channelsToProcess;

	/**
	 * which illumination directions to process, set in queryParameters
	 */
	final ArrayList< Illumination > illumsToProcess;

	/**
	 * which timepoints to process, set in queryParameters
	 */
	final ArrayList< TimePoint > timepointsToProcess;

	final SpimData2 spimData;
	
	protected int[] min, max;
	protected int downsampling;

	/**
	 * @param spimData
	 * @param anglesToPrcoess - which angles to segment
	 * @param channelsToProcess - which channels to segment in
	 * @param illumsToProcess - which illumination directions to segment
	 * @param timepointsToProcess - which timepoints were selected
	 */
	public BoundingBox(
			final SpimData2 spimData,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< Channel> channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< TimePoint > timepointsToProcess )
	{
		this.spimData = spimData;
		this.anglesToProcess = anglesToProcess;
		this.channelsToProcess = channelsToProcess;
		this.illumsToProcess = illumsToProcess;
		this.timepointsToProcess = timepointsToProcess;
		
		this.min = minStatic.clone();
		this.max = maxStatic.clone();
		this.downsampling = staticDownsampling;
	}
	
	/**
	 * Query the necessary parameters for the bounding box
	 * 
	 * @return
	 */
	public abstract boolean queryParameters();

	/**
	 * @param spimData
	 * @param anglesToPrcoess - which angles to segment
	 * @param channelsToProcess - which channels to segment in
	 * @param illumsToProcess - which illumination directions to segment
	 * @param timepointsToProcess - which timepoints were selected
	 * @return - a new instance without any special properties
	 */
	public abstract BoundingBox newInstance(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess );

	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();	
	
	@Override
	public long min( final int d ) { return min[ d ]; }

	@Override
	public void min( final long[] min )
	{
		for ( int d = 0; d < min.length; ++d )
			min[ d ] = this.min[ d ];
	}

	@Override
	public void min( final Positionable min ) { min.setPosition( this.min ); }

	@Override
	public long max( final int d ) { return max[ d ]; }

	@Override
	public void max( final long[] max )
	{
		for ( int d = 0; d < max.length; ++d )
			max[ d ] = this.max[ d ];
	}

	@Override
	public void max( final Positionable max ) { max.setPosition( this.max ); }

	@Override
	public double realMin( final int d ) { return min[ d ]; }

	@Override
	public void realMin( final double[] min )
	{
		for ( int d = 0; d < min.length; ++d )
			min[ d ] = this.min[ d ];
	}

	@Override
	public void realMin( final RealPositionable min ) { min.setPosition( this.min ); }

	@Override
	public double realMax( final int d ) { return this.max[ d ]; }

	@Override
	public void realMax( final double[] max )
	{
		for ( int d = 0; d < max.length; ++d )
			max[ d ] = this.max[ d ];
	}

	@Override
	public void realMax( final RealPositionable max ) { max.setPosition( this.max ); }

	@Override
	public int numDimensions() { return min.length; }

	@Override
	public void dimensions( final long[] dimensions )
	{
		for ( int d = 0; d < max.length; ++d )
			dimensions[ d ] = dimension( d );
	}

	@Override
	public long dimension( final int d ) { return this.max[ d ] - this.min[ d ] + 1; }
}
