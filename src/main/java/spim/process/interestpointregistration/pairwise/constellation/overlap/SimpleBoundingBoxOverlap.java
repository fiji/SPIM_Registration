package spim.process.interestpointregistration.pairwise.constellation.overlap;

import java.util.Map;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.FinalRealInterval;
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
