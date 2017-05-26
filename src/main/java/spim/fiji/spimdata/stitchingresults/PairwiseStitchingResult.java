package spim.fiji.spimdata.stitchingresults;

import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;



public class PairwiseStitchingResult <C extends Comparable< C >>
{
	
	public interface PairwiseQualityMeasure
	{
		public double getQuality();
	}
	
	public class CrossCorrelationPairwiseQualityMeasure implements PairwiseQualityMeasure
	{

		private double r;
		
		public CrossCorrelationPairwiseQualityMeasure(double r)
		{
			this.r = r;
		}
		
		@Override
		public double getQuality(){ return r; }
		
	}

	final private Pair< Group<C>, Group<C> > pair;
	final private AffineTransform3D transform;
	final private RealInterval boundingBox;
	final private double r;

	/**
	 * 
	 * @param pair - what was compared
	 * @param boundingBox - in which bounding box (in global space) was is compared
	 * @param transform - the transformation mapping A to B
	 * @param r - the correlation
	 */
	public PairwiseStitchingResult(
			final Pair< Group<C>, Group<C> > pair,
			final RealInterval boundingBox,
			final AffineGet transform,
			final double r )
	{
		this.pair = pair;
		this.boundingBox = boundingBox;
		this.transform = new AffineTransform3D();
		this.transform.set( transform.getRowPackedCopy() );
		this.r = r;
	}


	public Pair< Group<C>, Group<C> > pair() { return pair; }
	public AffineGet getTransform() { return transform; }
	public double r() { return r; }
	public AffineGet getInverseTransform(){ return transform.inverse(); }
	public RealInterval getBoundingBox(){ return boundingBox; }
}
