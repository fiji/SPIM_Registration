package spim.process.interestpointregistration.pairwise.constellation.overlap;

import java.util.List;
import java.util.Map;

import mpicbg.imglib.util.Util;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.FinalRealInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.boundingbox.BoundingBox;

public class SimpleBoundingBoxOverlap< V extends ViewId > implements OverlapDetection< V >
{
	final ViewRegistrations vrs;
	final Map< Integer, ? extends BasicViewSetup > vss;

	public SimpleBoundingBoxOverlap( final AbstractSpimData< ? extends AbstractSequenceDescription< ? extends BasicViewSetup, ?, ? > > spimData )
	{
		this.vss = spimData.getSequenceDescription().getViewSetups();
		this.vrs = spimData.getViewRegistrations();
	}
	public SimpleBoundingBoxOverlap( final SequenceDescription sd, final ViewRegistrations vrs )
	{
		this.vss = sd.getViewSetups();
		this.vrs = vrs;
	}

	@Override
	public boolean overlaps( final V view1, final V view2 )
	{
		final BoundingBox bb1 = getBoundingBox( view1, vss, vrs );
		final BoundingBox bb2 = getBoundingBox( view1, vss, vrs );
		
		

		if ( bb1 == null )
			throw new RuntimeException( "view1 has no image size" );

		if ( bb2 == null )
			throw new RuntimeException( "view2 has no image size" );

		return overlaps( bb1, bb2 );
	}

	public static boolean overlaps( final BoundingBox bb1, final BoundingBox bb2 )
	{
		for ( int d = 0; d < bb1.numDimensions(); ++d )
		{
			if (
				bb1.getMin()[ d ] < bb2.getMin()[ d ] && bb1.getMax()[ d ] < bb2.getMin()[ d ] ||
				bb1.getMin()[ d ] > bb2.getMax()[ d ] && bb1.getMax()[ d ] > bb2.getMax()[ d ] )
			{
				return false;
			}
		}

		return true;
	}

	public static < V extends ViewId > BoundingBox getBoundingBox(
			final V view,
			final Map< Integer, ? extends BasicViewSetup > vss,
			final ViewRegistrations vrs )
	{
		return getBoundingBox( vss.get( view.getViewSetupId() ), vrs.getViewRegistration( view ) );
	}
	
	public static BoundingBox getBoundingBox(Dimensions dims, AffineTransform3D transform)
	{
		final double[] min = new double[]{ 0, 0, 0 };
		final double[] max = new double[]{
				dims.dimension( 0 ) - 1,
				dims.dimension( 1 ) - 1,
				dims.dimension( 2 ) - 1 };

		for ( int d = 0; d < max.length; ++d )
			--max[ d ];

		final FinalRealInterval interval = transform.estimateBounds( new FinalRealInterval( min, max ) );

		final int[] minInt = new int[ 3 ];
		final int[] maxInt = new int[ 3 ];

		for ( int d = 0; d < min.length; ++d )
		{
			minInt[ d ] = (int)Math.round( interval.realMin( d ) ) - 1;
			maxInt[ d ] = (int)Math.round( interval.realMax( d ) ) + 1;
		}

		return new BoundingBox( minInt, maxInt );	
	}

	
	public static < V extends ViewId > BoundingBox getBoundingBox(
			final List<Dimensions> dims,
			final List<AffineTransform3D> transforms )
	{
	
		int numDimensions = dims.get( 0 ).numDimensions();
		int[] min = Util.getArrayFromValue( Integer.MAX_VALUE, numDimensions );
		int[] max = Util.getArrayFromValue( Integer.MIN_VALUE, numDimensions );
		BoundingBox bb = new BoundingBox( min, max );

		for (int i = 0; i < dims.size(); i++)
		{
			bb = mergeBoundingBoxes( bb, getBoundingBox( dims.get( i ), transforms.get( i ) ) );
		}
		
		return bb;
	}
	
	// merge two bounding boxes into a bounding box ranging from the smaller min to the larger max in each dimension
	public static BoundingBox mergeBoundingBoxes(BoundingBox bb1, BoundingBox bb2)
	{
		int[] min = new int[bb1.numDimensions()];
		int[] max = new int[bb1.numDimensions()];
		
		for (int d = 0; d < bb1.numDimensions(); d++)
		{
			min[d] = (int) Math.min( bb1.min( d ), bb2.min( d ) );
			max[d] = (int) Math.max( bb1.max( d ), bb2.max( d ) );
		}
		return new BoundingBox( min, max );
	}
	
	
	public static < V extends ViewId > BoundingBox getBoundingBox(
			final BasicViewSetup vs,
			final ViewRegistration vr )
	{
		if ( !vs.hasSize() )
			return null;

		final Dimensions size = vs.getSize();

		final double[] min = new double[]{ 0, 0, 0 };
		final double[] max = new double[]{
				size.dimension( 0 ) - 1,
				size.dimension( 1 ) - 1,
				size.dimension( 2 ) - 1 };

		for ( int d = 0; d < max.length; ++d )
			--max[ d ];

		vr.updateModel();

		final FinalRealInterval interval = vr.getModel().estimateBounds( new FinalRealInterval( min, max ) );

		final int[] minInt = new int[ 3 ];
		final int[] maxInt = new int[ 3 ];

		for ( int d = 0; d < min.length; ++d )
		{
			minInt[ d ] = (int)Math.round( interval.realMin( d ) ) - 1;
			maxInt[ d ] = (int)Math.round( interval.realMax( d ) ) + 1;
		}

		return new BoundingBox( minInt, maxInt );
	}
}
