package spim.fiji.plugin.fusion;

import java.util.List;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RealPositionable;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ComplexType;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.export.ImgExport;

public abstract class BoundingBox implements Interval
{
	public static int staticDownsampling = 1;
	
	public static int defaultMin[] = { 0, 0, 0 };
	public static int defaultMax[] = { 0, 0, 0 };
	public static int defaultRangeMin[] = { 0, 0, 0 };
	public static int defaultRangeMax[] = { 0, 0, 0 };
	
	public static String[] pixelTypes = new String[]{ "32-bit floating point", "16-bit unsigned integer" };
	public static int defaultPixelType = 0;
	protected int pixelType = 0;

	public static String[] imgTypes = new String[]{ "ArrayImg", "PlanarImg (large images, easy to display)", "CellImg (large images)" };
	public static int defaultImgType = 1;
	protected int imgtype = 1;

	/**
	 * which angles to process, set in queryParameters
	 */
	protected final List< Angle > anglesToProcess;

	/**
	 * which channels to process, set in queryParameters
	 */
	protected final List< Channel> channelsToProcess;

	/**
	 * which illumination directions to process, set in queryParameters
	 */
	protected final List< Illumination > illumsToProcess;

	/**
	 * which timepoints to process, set in queryParameters
	 */
	protected final List< TimePoint > timepointsToProcess;

	protected final SpimData2 spimData;
	
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
			final List< Angle > anglesToProcess,
			final List< Channel> channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< TimePoint > timepointsToProcess )
	{
		this.spimData = spimData;
		this.anglesToProcess = anglesToProcess;
		this.channelsToProcess = channelsToProcess;
		this.illumsToProcess = illumsToProcess;
		this.timepointsToProcess = timepointsToProcess;
		
		this.min = defaultMin.clone();
		this.max = defaultMax.clone();
		this.downsampling = staticDownsampling;
	}
	
	/**
	 * Query the necessary parameters for the bounding box
	 * 
	 * @return
	 */
	public abstract boolean queryParameters( final Fusion fusion, final ImgExport imgExport );

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
			final List<Angle> anglesToProcess,
			final List<Channel> channelsToProcess,
			final List<Illumination> illumsToProcess,
			final List<TimePoint> timepointsToProcess );

	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();

	/**
	 * Called before the XML is potentially saved
	 */
	public abstract void cleanUp( LoadParseQueryXML result );

	public int getDownSampling() { return downsampling; }
	
	public int getPixelType() { return pixelType; }
	
	public int getImgType() { return imgtype; }
	
	public < T extends ComplexType< T > & NativeType < T > > ImgFactory< T > getImgFactory( final T type )
	{
		final ImgFactory< T > imgFactory;
		
		if ( this.getImgType() == 0 )
			imgFactory = new ArrayImgFactory<T>();
		else if ( this.getImgType() == 1 )
			imgFactory = new ImagePlusImgFactory<T>();
		else
			imgFactory = new CellImgFactory<T>( 256 );

		return imgFactory;
	}
	
	/**
	 * @return - the final dimensions including downsampling of this bounding box (to instantiate an img)
	 */
	public long[] getDimensions()
	{
		final long[] dim = new long[ this.numDimensions() ];
		this.dimensions( dim );
		
		for ( int d = 0; d < this.numDimensions(); ++d )
			dim[ d ] /= this.getDownSampling();
		
		return dim;
	}
	
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
