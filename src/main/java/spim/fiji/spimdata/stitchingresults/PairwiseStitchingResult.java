package spim.fiji.spimdata.stitchingresults;

import java.util.Set;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;



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
	
	
	
	final private Pair< Set<C>, Set<C> > pair;
	final private AffineTransform3D transform;
	final private double r;
	

	public PairwiseStitchingResult( final Pair< Set<C>, Set<C> > pair, final AffineGet transform, final double r )
	{
		this.pair = pair;
		this.transform = new AffineTransform3D();
		this.transform.set( transform.getRowPackedCopy() );
		this.r = r;
	}


	public Pair< Set<C>, Set<C> > pair() { return pair; }
	public AffineGet getTransform() { return transform; }
	public double r() { return r; }	
	public AffineGet getInverseTransform(){	return transform.inverse();	}
	
}
