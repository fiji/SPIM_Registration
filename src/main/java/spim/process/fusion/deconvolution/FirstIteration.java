package spim.process.fusion.deconvolution;

import java.util.ArrayList;
import java.util.concurrent.Callable;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import net.imglib2.view.Views;
import spim.process.fusion.ImagePortion;

/**
 * Fuse one portion of a paralell fusion, supports no weights
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class FirstIteration implements Callable< RealSum >
{
	final ImagePortion portion;
	final RandomAccessibleInterval< FloatType > psi;
	final ArrayList< RandomAccessibleInterval< FloatType > > imgs, weights;

	final IterableInterval< FloatType > psiIterable;
	final ArrayList< IterableInterval< FloatType > > iterableImgs, iterableWeights;

	final RealSum realSum;

	boolean compatibleIteration;

	public FirstIteration(
			final ImagePortion portion,
			final RandomAccessibleInterval< FloatType > psi,
			final ArrayList< RandomAccessibleInterval< FloatType > > imgs,
			final ArrayList< RandomAccessibleInterval< FloatType > > weights )
	{
		this.portion = portion;
		this.psi = psi;
		this.imgs = imgs;
		this.weights = weights;

		this.psiIterable = Views.iterable( psi );
		this.iterableImgs = new ArrayList< IterableInterval< FloatType > >();
		this.iterableWeights = new ArrayList< IterableInterval< FloatType > >();

		this.realSum = new RealSum();

		if ( imgs.size() != weights.size() )
			throw new RuntimeException( "Number of weights and images must be equal." );

		compatibleIteration = true;

		for ( final RandomAccessibleInterval< FloatType > img : imgs )
		{
			final IterableInterval< FloatType > imgIterable = Views.iterable( img );

			if ( !psiIterable.iterationOrder().equals( imgIterable.iterationOrder() ) )
				compatibleIteration = false;

			this.iterableImgs.add( imgIterable );
		}

		for ( final RandomAccessibleInterval< FloatType > weight : weights )
		{
			final IterableInterval< FloatType > weightIterable = Views.iterable( weight );

			if ( !psiIterable.iterationOrder().equals( weightIterable.iterationOrder() ) )
				compatibleIteration = false;

			for ( final IterableInterval< FloatType > imgIterable : iterableImgs )
				if ( !imgIterable.iterationOrder().equals( weightIterable.iterationOrder() ) )
					compatibleIteration = false;

			this.iterableWeights.add( weightIterable );
		}
	}
	
	@Override
	public RealSum call() throws Exception 
	{
		final Cursor< FloatType > psiCursor = psiIterable.localizingCursor();
		psiCursor.jumpFwd( portion.getStartPosition() );

		final int m = iterableImgs.size();

		if ( compatibleIteration )
		{
			final ArrayList< Cursor< FloatType > > cursorWeights = new ArrayList< Cursor< FloatType > >();
			final ArrayList< Cursor< FloatType > > cursorImgs = new ArrayList< Cursor< FloatType > >();

			for ( final IterableInterval< FloatType > img : iterableImgs )
			{
				final Cursor< FloatType > imgCursor = img.cursor();
				imgCursor.jumpFwd( portion.getStartPosition() );
				cursorImgs.add( imgCursor );
			}

			for ( final IterableInterval< FloatType > weight : iterableWeights )
			{
				final Cursor< FloatType > weightCursor = weight.cursor();
				weightCursor.jumpFwd( portion.getStartPosition() );
				cursorWeights.add( weightCursor );
			}

			for ( int j = 0; j < portion.getLoopSize(); ++j )
				compatibleLoop( psiCursor, cursorWeights, cursorImgs, realSum, m );
		}
		else
		{
			final ArrayList< RandomAccess< FloatType > > randomAccessWeights = new ArrayList< RandomAccess< FloatType > >();
			final ArrayList< RandomAccess< FloatType > > randomAccessImgs = new ArrayList< RandomAccess< FloatType > >();

			for ( final RandomAccessibleInterval< FloatType > img : imgs )
				randomAccessImgs.add( img.randomAccess() );

			for ( final RandomAccessibleInterval< FloatType > weight : weights )
				randomAccessWeights.add( weight.randomAccess() );

			for ( int j = 0; j < portion.getLoopSize(); ++j )
				incompatibleLoop( psiCursor, randomAccessWeights, randomAccessImgs, realSum, m );
		}

		return realSum;
	}

	private static final void compatibleLoop(
			final Cursor< FloatType > psiCursor,
			final ArrayList< Cursor< FloatType > > cursorWeights,
			final ArrayList< Cursor< FloatType > > cursorImgs,
			final RealSum realSum,
			final int m )
	{
		double sum = 0;
		double sumW = 0;

		for ( int j = 0; j < m; ++j )
		{
			final double w = cursorWeights.get( j ).next().get();
			final double i = cursorImgs.get( j ).next().get();

			sum += i*w;
			sumW += w;
		}

		if ( sumW > 0 )
		{
			final double i = sum / sumW;
			realSum.add( i );
			psiCursor.next().set( (float) i );
		}
		else
		{
			psiCursor.fwd();
		}
	}

	private static final void incompatibleLoop(
			final Cursor< FloatType > psiCursor,
			final ArrayList< RandomAccess< FloatType > > randomAccessWeights,
			final ArrayList< RandomAccess< FloatType > > randomAccessImgs,
			final RealSum realSum,
			final int m )
	{
		final FloatType p = psiCursor.next();
		double sum = 0;
		double sumW = 0;

		for ( int j = 0; j < m; ++j )
		{
			final RandomAccess< FloatType > randomAccessWeight = randomAccessWeights.get( j );
			final RandomAccess< FloatType > randomAccessImg = randomAccessImgs.get( j );

			randomAccessWeight.setPosition( psiCursor );
			randomAccessImg.setPosition( psiCursor );
			
			final double w = randomAccessWeight.get().get();
			final double i = randomAccessImg.get().get();

			sum += i*w;
			sumW += w;
		}

		if ( sumW > 0 )
		{
			final double i = sum / sumW;
			realSum.add( i );
			p.set( (float)i );
		}
	}
}
