package spim.process.fusion.deconvolution;

import ij.ImageJ;
import ij.ImagePlus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import mpicbg.spim.io.IOFunctions;
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
import spim.Threads;
import spim.process.fusion.FusionHelper;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.weights.BlendingRealRandomAccess;
import spim.process.fusion.weights.NormalizingRandomAccessibleInterval;

public class WeightNormalizer
{
	// for additional smoothing of weights in areas where many views contribute less than 100%
	public static float maxDiffRange = 0.1f;
	public static float scalingRange = 0.05f;
	public static boolean additionalSmoothBlending = false;

	final List< RandomAccessibleInterval< FloatType > > weights;
	final Img< FloatType > sumWeights;

	int minOverlappingViews;
	double avgOverlappingViews;

	public WeightNormalizer( final List< RandomAccessibleInterval< FloatType > > weights )
	{
		this.weights = weights;
		this.sumWeights = null;
	}

	public WeightNormalizer( final List< RandomAccessibleInterval< FloatType > > weights, final ImgFactory< FloatType > factory )
	{
		this.weights = weights;
		this.sumWeights = factory.create( weights.get( 0 ), new FloatType() );
	}

	public int getMinOverlappingViews() { return minOverlappingViews; }
	public double getAvgOverlappingViews() { return avgOverlappingViews; }
	public Img< FloatType > getSumWeights() { return sumWeights; }

	public boolean process()
	{
		// split up into many parts for multithreading
		final Vector< ImagePortion > portions = FusionHelper.divideIntoPortions( Views.iterable( weights.get( 0 ) ).size(), Threads.numThreads() * 2 );

		// set up executor service
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );
		final ArrayList< Callable< double[] > > tasks = new ArrayList< Callable< double[] > >();

		for ( final ImagePortion portion : portions )
		{
			if ( sumWeights == null )
				tasks.add( new ApplyDirectly( portion, additionalSmoothBlending, maxDiffRange, scalingRange ) );
			else
				tasks.add( new ComputeSumImage( portion, sumWeights ) );
		}

		// run threads
		try
		{
			// invokeAll() returns when all tasks are complete
			final List< Future< double[] > > futures = taskExecutor.invokeAll( tasks );
			
			this.minOverlappingViews = weights.size();
			this.avgOverlappingViews = 0;
			
			for ( final Future< double[] > f : futures )
			{
				final double[] minAvg = f.get();
				
				this.minOverlappingViews = Math.min( this.minOverlappingViews, (int)Math.round( minAvg[ 0 ] ) );
				this.avgOverlappingViews += minAvg[ 1 ];
			}
			
			this.avgOverlappingViews /= futures.size();
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to compute weight normalization for deconvolution: " + e );
			e.printStackTrace();
			return false;
		}

		taskExecutor.shutdown();

		// set the normalizing interval
		if ( sumWeights != null )
		{
			for ( int i = 0; i < weights.size(); ++i )
			{
				final RandomAccessibleInterval< FloatType > w = weights.get( i );
				final NormalizingRandomAccessibleInterval< FloatType > nw = new NormalizingRandomAccessibleInterval< FloatType >(
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

		return true;
	}

	final private static void applySmooth( final ArrayList< Cursor< FloatType > > cursors, double sumW, final float maxDiffRange, final float scalingRange )
	{
		for ( final Cursor< FloatType > c : cursors )
			c.get().set( smoothWeights( c.get().get(), sumW, maxDiffRange, scalingRange ) );
	}

	final public static float smoothWeights( final float w, final double sumW, final float maxDiffRange, final float scalingRange )
	{
		if ( sumW <= 0 )
			return 0;

		final float idealValue = (float)( w / sumW );

		final float diff = w - idealValue;

		// map diff: 0 ... maxDiffRange >> 1 ...  0, rest negative
		final float y = Math.max( 0, ( maxDiffRange - Math.abs( diff ) ) * ( 1.0f / maxDiffRange ) );

		// scale with the value of w
		final float scale = y * w * scalingRange;

		// final function is a scaling down
		return ( Math.min( w, idealValue ) - (float)scale );
	}

	final private static void applyHard( final ArrayList< Cursor< FloatType > > cursors, final double sumW )
	{
		if ( sumW > 1 )
		{
			for ( final Cursor< FloatType > c : cursors )
				c.get().set( hardWeights( c.get().get(), sumW )  );
		}
	}

	final public static float hardWeights( final float w, final double sumW )
	{
		return (float)( w / sumW );
	}

	final private class ApplyDirectly implements Callable< double[] >
	{
		final ImagePortion portion;
		final boolean additionalSmoothBlending;
		final float maxDiffRange;
		final float scalingRange;

		public ApplyDirectly(
				final ImagePortion portion,
				final boolean additionalSmoothBlending,
				final float maxDiffRange,
				final float scalingRange )
		{
			this.portion = portion;
			this.additionalSmoothBlending = additionalSmoothBlending;
			this.maxDiffRange = maxDiffRange;
			this.scalingRange = scalingRange;
		}

		@Override
		public double[] call() throws Exception
		{
			final ArrayList< Cursor< FloatType > > cursors = new ArrayList< Cursor< FloatType > >();

			for ( final RandomAccessibleInterval< FloatType > imgW : weights )
			{
				final Cursor< FloatType > c = Views.iterable( imgW ).cursor();
				c.jumpFwd( portion.getStartPosition() );
				cursors.add( c );
			}

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

				// something in between ... I would say, now we have hard edges where the image stacks end
				if ( additionalSmoothBlending )
					applySmooth( cursors, sumW, maxDiffRange, scalingRange );
				else
					applyHard( cursors, sumW );
			}

			final double avgNumViews = (double)countViews / (double)( portion.getLoopSize() );

			return new double[]{ minNumViews, avgNumViews };
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
