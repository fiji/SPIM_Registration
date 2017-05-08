package spim.process.fusion.boundingbox.overlap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.boundingbox.BoundingBox;

public abstract class AbstractMaxBoundingBoxDetermination < V extends ViewId > implements BoundingBoxDetermination<V>
{

	protected final ViewRegistrations vrs;
	protected final Map< Integer, ? extends BasicViewSetup > vss;


	public AbstractMaxBoundingBoxDetermination( final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ?, ? > > spimData )
	{
		this.vss = spimData.getSequenceDescription().getViewSetups();
		this.vrs = spimData.getViewRegistrations();
	}


	public AbstractMaxBoundingBoxDetermination( final SequenceDescription sd, final ViewRegistrations vrs )
	{
		this.vss = sd.getViewSetups();
		this.vrs = vrs;
	}


	@Override
	public BoundingBox getMaxBoundingBox(Collection<? extends Collection< V > > viewGroups)
	{
		Collection<Collection<Pair<RealInterval, AffineGet>>> viewGroupsInner = new ArrayList<>();
		
		for (Collection< V > group : viewGroups)
		{
			ArrayList< Pair<RealInterval, AffineGet> > viewGroupInnerI = new ArrayList<>();
			
			for (ViewId view : group)
			{
				Dimensions viewSize = vss.get( view.getViewSetupId() ).getSize();
				if (viewSize == null)
				{
					System.err.println( "WARNING: View " + view + " has no size, ignoring it." );
					continue;
				}

				FinalInterval interval = new FinalInterval( viewSize );				
				AffineTransform3D model = vrs.getViewRegistration( view ).getModel();
				
				ValuePair< RealInterval, AffineGet > viewI = new ValuePair<>( interval, model );
				viewGroupInnerI.add( viewI );
			}
			viewGroupsInner.add( viewGroupInnerI );
		}
		
		final RealInterval maxBoundingInterval = getMaxBoundingInterval( viewGroupsInner );
		
		final int[] min = new int[maxBoundingInterval.numDimensions()];
		final int[] max = new int[maxBoundingInterval.numDimensions()];
		
		for (int d = 0; d < maxBoundingInterval.numDimensions(); d++)
		{
			min[d] = (int) Math.floor( maxBoundingInterval.realMin( d ) );
			max[d] = (int) Math.ceil( maxBoundingInterval.realMax( d ) );
		}
		
		return new BoundingBox( min, max );
	}


	/**
	 * Calculate the boundary interval of all views (view := interval + transformation)
	 * in all view groups in viewGroups
	 * 
	 * @param viewGroups
	 */
	public static RealInterval getMaxBoundingInterval(Collection<Collection<Pair<RealInterval, AffineGet>>> viewGroups)
	{
		double[] min = null;
		double[] max = null;
		Integer n = null;

		for (Collection< Pair<RealInterval, AffineGet> > group : viewGroups)
			for (Pair<RealInterval, AffineGet> view : group)
			{
				FinalRealInterval transformedBounds = estimateBounds( view.getA(), view.getB() );
				if (n == null)
					n = transformedBounds.numDimensions();


				double[] minI = new double[n];
				double[] maxI = new double[n];

				transformedBounds.realMin( minI );
				transformedBounds.realMax( maxI );

				if (max == null)
				{
					min = minI.clone();
					max = maxI.clone();
				}

				Util.max( max, maxI );
				Util.min( min, minI );
			}

		if (min == null)
			return null;

		return new FinalRealInterval( min, max );
	}


	/**
	 * Calculate the boundary interval of an interval after it has been
	 * transformed by transform.
	 * 
	 * generalized version of code in {@link AffineTransform3D}
	 * 
	 * @param interval
	 * @param transform
	 */
	public static FinalRealInterval estimateBounds( final RealInterval interval, final AffineGet transform )
	{
		final int n =  interval.numDimensions();
		final double[] min = new double[ n];
		final double[] max = new double[ n ];
		final double[] rMin = new double[ n ];
		final double[] rMax = new double[ n ];

		interval.realMin( min );
		interval.realMax( max );
		
		for (int d = 0; d < n; d++)
		{
			rMin[ d ] = Double.MAX_VALUE;
			rMax[ d ] = -Double.MAX_VALUE;
		}

		final double[] f = new double[ n ];
		final double[] g = new double[ n ];

		for (int i = 0; i < (int) Math.pow( 2, n ); i++)
		{
			int j = i;
			for (int d = 0; d < n; d++)
			{
				f[d] = j % 2 == 0 ? min[d] : max[d];
				j /= 2;
			}
			transform.apply( f, g );
			Util.min( rMin, g );
			Util.max( rMax, g );
		}

		return new FinalRealInterval( rMin, rMax );
	}

	
	/**
	 * get String representation of RealInterval TODO: PR to ImgLib2 ({@link Util}) ?
	 * @param interval
	 * @return
	 */
	public static String printRealInterval( final RealInterval interval )
	{
		String out = "(Interval empty)";

		if ( interval == null || interval.numDimensions() == 0 )
			return out;

		out = "[" + interval.realMin( 0 );

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + interval.realMin( i );

		out += "] -> [" + interval.realMax( 0 );

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + interval.realMax( i );

		out += "], dimensions (" + (interval.realMax( 0 ) - interval.realMin( 0 ));

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + (interval.realMax( i ) - interval.realMin( i ));

		out += ")";

		return out;
	}

	public static void main(String[] args)
	{
		Collection<Collection<Pair<RealInterval, AffineGet>>> viewGroups = new ArrayList<>();
		
		Collection<Pair<RealInterval, AffineGet>> viewGroup1 = new ArrayList<>();		
		viewGroup1.add( new ValuePair<>(Intervals.createMinMaxReal(  0,0,1,1 ), new Translation( 0, 0 )) );
		AffineTransform2D rot = new AffineTransform2D();
		rot.rotate( Math.PI / 4 );
		viewGroup1.add( new ValuePair<>(Intervals.createMinMaxReal(  0,0,1,1 ), rot) );
		
		Collection<Pair<RealInterval, AffineGet>> viewGroup2 = new ArrayList<>();		
		viewGroup2.add( new ValuePair<>(Intervals.createMinMaxReal(  0,0,1,1 ), new Translation( -1, 0 )) );
		viewGroup2.add( new ValuePair<>(Intervals.createMinMaxReal(  0,0,1,1 ), new Translation( 0, 1 )) );
		
		
		viewGroups.add( viewGroup1 );
		//viewGroups.add( viewGroup2 );
		
		System.out.println( printRealInterval( getMaxBoundingInterval( viewGroups ) ) );
		
	}


}
