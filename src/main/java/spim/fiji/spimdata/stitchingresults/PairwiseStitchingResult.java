package spim.fiji.spimdata.stitchingresults;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;


public class PairwiseStitchingResult <C extends Comparable< C >>
{
	final private Pair< C, C > pair;
	final private AffineGet transform;
	final private double r;
	

	public PairwiseStitchingResult( final Pair< C, C > pair, final AffineGet transform, final double r )
	{
		this.pair = pair;
		this.transform = new AffineTransform3D();
		((AffineTransform3D)this.transform).set( transform.getRowPackedCopy() );
		this.r = r;
	}

	public Pair< C, C > pair() { return pair; }
	public AffineGet getTransform() { return transform; }
	public double r() { return r; }

	/*
	public double[] negativeRelationVector()
	{
		final double[] tmp = new double[ 3 ];
		for ( int d = 0; d < tmp.length; ++d )
			tmp[ d ] = -transform.get( d, 3);
		return tmp;
	}
	*/
}
