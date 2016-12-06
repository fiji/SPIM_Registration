package spim.process.cache;

import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.BenchmarkHelper;
import net.imglib2.util.Fraction;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import spim.process.cache.LocalCachedImgCells.CellCache;
import spim.process.fusion.weights.Blending;
import spim.process.fusion.weights.TransformedRealRandomAccessibleInterval;

public class TestCache2
{

	public static void main( String[] args )
	{
		new ImageJ();
		final long[] min = new long[]{ 100, 50, 10 };

		final FinalInterval blendingInterval = new FinalInterval( new long[]{ 0, 0, 0 }, new long[]{ 350, 440, 530 } );
		final FinalInterval viewInterval = new FinalInterval( min, new long[]{ 350, 440, 530 } );

		final Blending blend = new Blending(
				blendingInterval,
				new float[]{ 100, 0, 20 },
				new float[]{ 12, 150, 30 } );

		final TransformedRealRandomAccessibleInterval< FloatType> imgIn = new TransformedRealRandomAccessibleInterval< FloatType >(
				blend,
				new FloatType( -1 ),
				viewInterval,
				new AffineTransform3D(),
				new long[]{ 0, 0, 0 } );

		//BenchmarkHelper.benchmarkAndPrint( 10, true, () -> tranverse( imgIn ) );
		
		final long[] minNeg = new long[ min.length ];
		
		for ( int d = 0; d < min.length; ++d )
			minNeg[ d ] = -min[ d ];
		final RandomAccessibleInterval< FloatType  > img = imgIn;//Views.translate( imgIn, minNeg );
		
		// maybe try to reimplement and simplify that one
		LocalCellCache globalCache = new LocalCellCache();
		FloatCacheArrayLoader loader = new FloatCacheArrayLoader( img );
		final CellCache< FloatArray > c = globalCache.new CellCacheImpl< FloatArray >( loader );
		final long[] dimensions = Intervals.dimensionsAsLongArray( img );
		final int[] cellDimensions = Util.getArrayFromValue( 32, img.numDimensions() );
		cellDimensions[ 0 ] = ( int ) dimensions[ 0 ];
		cellDimensions[ 1 ] = ( int ) dimensions[ 1 ];
		cellDimensions[ cellDimensions.length - 1 ] = 1;
		
		final LocalCachedImgCells< FloatArray > cells = new LocalCachedImgCells<>( c, new Fraction(), dimensions, cellDimensions );
		final LocalCachedCellImg< FloatType, FloatArray > img2 = new LocalCachedCellImg<>( cells );
		final FloatType linkedType = new FloatType( img2 );
		img2.setLinkedType( linkedType );

		final RandomAccessibleInterval< FloatType > img3 = Views.translate( img2, min );

		//BenchmarkHelper.benchmarkAndPrint( 10, true, () -> tranverse( img2 ) );
		//BenchmarkHelper.benchmarkAndPrint( 10, true, () -> tranverse( img3 ) );

		ImagePlus imp = ImageJFunctions.show( img3 );
		imp.setDisplayRange( 0, 1 );
		imp.updateAndDraw();
		System.out.println( Util.printInterval( img3 ) );

		ImagePlus impIn = ImageJFunctions.show( imgIn );
		impIn.setTitle( "original" );
		impIn.setDisplayRange( 0, 1 );
		impIn.updateAndDraw();
		System.out.println( Util.printInterval( imgIn ) );
		

		ImagePlus impRaw = ImageJFunctions.show( img2 );
		impRaw.setTitle( "untranslated" );
		impRaw.setDisplayRange( 0, 1 );
		impRaw.updateAndDraw();
		System.out.println( Util.printInterval( img2 ) );
		

	}

	public static void tranverse( final RandomAccessibleInterval< FloatType > img )
	{
		for ( final FloatType t : Views.iterable( img ) )
			t.get();
	}
	
	static class FloatCacheArrayLoader implements CacheArrayLoader< FloatArray >
	{
		private RandomAccessible< FloatType > generator;

		private FloatArray theEmptyArray;

		public FloatCacheArrayLoader( RandomAccessible< FloatType > generator )
		{
			this.generator = generator;
			this.theEmptyArray = new FloatArray( 1 );
		}

		@Override
		public FloatArray emptyArray( int[] dimensions )
		{
			int numEntities = 1;
			for ( int i = 0; i < dimensions.length; ++i )
				numEntities *= dimensions[ i ];
			if ( theEmptyArray.getCurrentStorageArray().length < numEntities )
				theEmptyArray = new FloatArray( numEntities );
			return theEmptyArray;
		}

		@Override
		public int getBytesPerElement()
		{
			return 4;
		}

		@Override
		public FloatArray loadArray( int[] dimensions, long[] min ) throws InterruptedException
		{
			int numEntities = ( int ) Intervals.numElements( dimensions );
			float[] data = new float[ numEntities ];
			long[] max = min.clone();
			for ( int d = 0; d < max.length; ++d )
				max[ d ] += dimensions[ d ] - 1;
			int i = 0;
			for ( FloatType t : Views.flatIterable( Views.interval( generator, new FinalInterval( min, max ) ) ) )
				data[ i++ ] = t.get();
			return new FloatArray( data );
		}
	}
}
