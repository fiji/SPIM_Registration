package spim.process.fusion.deconvolution.normalize;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.transformed.weights.BlendingRealRandomAccess;

public class WeightNormalizerPartlyVirtual extends WeightNormalizer
{
	final Img< FloatType > sumWeights;

	public WeightNormalizerPartlyVirtual( final List< RandomAccessibleInterval< FloatType > > weights, final ImgFactory< FloatType > factory )
	{
		super( weights );
		this.sumWeights = factory.create( weights.get( 0 ), new FloatType() );
	}

	public Img< FloatType > getSumWeights() { return sumWeights; }

	@Override
	public void adjustForOSEM( final double osemspeedup )
	{
		for ( final RandomAccessibleInterval< FloatType > w : weights )
			((NormalizingPartyVirtualRandomAccessibleInterval< FloatType >) w).setOSEMspeedup( osemspeedup );
	}

	@Override
	protected Callable< double[] > createWeightAdjustmentThread(
			final ImagePortion portion, 
			final boolean additionalSmoothBlending,
			final float maxDiffRange, 
			final float scalingRange)
	{
		return new ComputeSumImage( portion, sumWeights );
	}

	@Override
	protected void postProcess()
	{
		for ( int i = 0; i < weights.size(); ++i )
		{
			final RandomAccessibleInterval< FloatType > w = weights.get( i );
			final NormalizingPartyVirtualRandomAccessibleInterval< FloatType > nw = new NormalizingPartyVirtualRandomAccessibleInterval< FloatType >(
					w,
					sumWeights,
					1.0,
					additionalSmoothBlending,
					maxDiffRange,
					scalingRange,
					new FloatType());
			weights.set( i, nw );
		}
	}

	final private class ComputeSumImage implements Callable< double[] >
	{
		final ImagePortion portion;
		final Img< FloatType > sumWeights;

		public ComputeSumImage( final ImagePortion portion, final Img< FloatType > sumWeights )
		{ 
			this.portion = portion;
			this.sumWeights = sumWeights;
		}

		@Override
		public double[] call() throws Exception
		{
			final ArrayList< Cursor< FloatType > > cursors = new ArrayList< Cursor< FloatType > >(); 
			final RandomAccess< FloatType > ra = sumWeights.randomAccess();

			for ( int i = 0; i < weights.size(); ++i )
			{
				final RandomAccessibleInterval< FloatType > imgW = weights.get( i );
				final Cursor< FloatType > c;

				if ( i == 0 )
					c = Views.iterable( imgW ).localizingCursor();
				else
					c = Views.iterable( imgW ).cursor();

				c.jumpFwd( portion.getStartPosition() );
				cursors.add( c );
			}

			// the first one is a localizablecursor
			final Cursor< FloatType > firstCursor = cursors.get( 0 );

			int minNumViews = cursors.size();
			long countViews = 0;

			for ( long j = 0; j < portion.getLoopSize(); ++j )
			{
				double sumW = 0;
				int count = 0;

				for ( final Cursor< FloatType > c : cursors )
				{
					final float w = c.next().get();
					sumW += w;

					if ( w > 0 )
						++count;
				}

				countViews += count;
				minNumViews = Math.min( minNumViews, count );

				ra.setPosition( firstCursor );
				ra.get().set( (float)sumW );
			}

			final double avgNumViews = (double)countViews / (double)( portion.getLoopSize() );

			return new double[]{ minNumViews, avgNumViews };
		}
	}

	public static void main( String[] args )
	{
		new ImageJ();
		int size = 1000;

		final ArrayList< Img< FloatType > > imgs = new ArrayList<Img< FloatType >>();
		final ArrayList< Cursor< FloatType > > cursors = new ArrayList<Cursor<FloatType>>();
		final ArrayList< Interval > intervals = new ArrayList<Interval>();
		final ArrayList< RealRandomAccess< FloatType > > blends = new ArrayList<RealRandomAccess<FloatType>>();

		final float blending[] = new float[]{ 30, 10 };
		final float border[] = new float[]{ 0, 0 };

		intervals.add( new FinalInterval( new long[]{ 50, 0 }, new long[]{ 450, 300 } ) );
		intervals.add( new FinalInterval( new long[]{ 250, 150 }, new long[]{ 700, 800 } ) );
		intervals.add( new FinalInterval( new long[]{ 350, 50 }, new long[]{ 800, 400 } ) );
/*
		Random rnd = new Random( 35467 );
		for ( int i = 0; i < 5; ++i )
		{
			final int x1 = rnd.nextInt( size )- size/2;
			final int y1 = rnd.nextInt( size )- size/2;

			int x2, y2;
			
			do 
			{
				x2 = rnd.nextInt( size * 2 );
			} while ( x2 <= x1 );
			
			do
			{
				y2 = rnd.nextInt( size * 2  );
			} while ( y2 <= y1 );

			System.out.println( x1 + ", " + y1 + " >>> " + x2 + ", " + y2 );
			intervals.add( new FinalInterval( new long[]{ x1, y1 }, new long[]{ x2, y2 } ) );
		}
*/
		for ( int i = 0; i < intervals.size(); ++i )
		{
			imgs.add( ArrayImgs.floats( size, size ) );
			cursors.add( imgs.get( i ).localizingCursor() );
		}

		for ( final Interval i : intervals )
			blends.add( new BlendingRealRandomAccess( i, border, blending ) );

		while ( cursors.get( 0 ).hasNext() )
		{
			double sumW = 0;

			for ( int i = 0; i < intervals.size(); ++i )
			{
				final Cursor< FloatType > c = cursors.get( i );
				final RealRandomAccess< FloatType > b = blends.get( i );
	
				c.fwd();
				b.setPosition( c );
				final float w = b.get().getRealFloat();
				c.get().set( w );

				if ( w > 0 )
					sumW += w;
			}

			// something in between ... I would say, now we have hard edges where the image stacks end
			applyHard( cursors, sumW );
			//applySmooth( cursors, sumW, maxDiffRange, scalingRange );
		}

		for ( final Img< FloatType > img : imgs )
		{
			ImagePlus imp = ImageJFunctions.show( img );
			imp.resetDisplayRange();
			imp.updateAndDraw();
		}
	}

}
