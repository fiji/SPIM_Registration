package spim.process.fusion.deconvolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ij.ImageJ;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
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
				tasks.add( new ApplyDirectly( portion ) );
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
				final NormalizingRandomAccessibleInterval< FloatType > nw = new NormalizingRandomAccessibleInterval< FloatType >( w, sumWeights, new FloatType() );
				weights.set( i, nw );
			}
		}

		return true;
	}

	final private static void apply( final ArrayList< Cursor< FloatType > > cursors, final double sumW )
	{
		for ( final Cursor< FloatType > c : cursors )
			c.get().set( (float)( c.get().get() / sumW ) );
	}

	final private class ApplyDirectly implements Callable< double[] >
	{
		final ImagePortion portion;

		public ApplyDirectly( final ImagePortion portion ) { this.portion = portion; }

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
				//if ( sumW > 1 )
					apply( cursors, sumW );
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

				if ( sumW > 1 )
					ra.get().set( (float)sumW );
				else
					ra.get().setOne();
			}

			final double avgNumViews = (double)countViews / (double)( portion.getLoopSize() );

			return new double[]{ minNumViews, avgNumViews };
		}
	}

	public static void main( String[] args )
	{
		new ImageJ();
		
		Img< FloatType > img = ArrayImgs.floats( 500, 500 );
		BlendingRealRandomAccess blend = new BlendingRealRandomAccess(
				img,
				new float[]{ 100, 0 },
				new float[]{ 12, 150 } );
		
		Cursor< FloatType > c = img.localizingCursor();
		
		while ( c.hasNext() )
		{
			c.fwd();
			blend.setPosition( c );
			c.get().setReal( blend.get().getRealFloat() );
		}
		
		ImageJFunctions.show( img );
	}

}
