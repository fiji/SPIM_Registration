package spim.fiji;

import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;

public class ImgLib2Temp
{
	// TODO: Remove when Imglib2 is updated in Fiji
	public static FinalInterval getIntervalFromDimension( final Dimensions dim )
	{
		final long[] min = new long[ dim.numDimensions() ];
		final long[] max = new long[ dim.numDimensions() ];
		for ( int d = 0; d < dim.numDimensions(); ++d )
		{
			min[ d ] = 0;
			max[ d ] = dim.dimension( d ) - 1;
		}

		return new FinalInterval( min, max );
	}
	
	public interface Pair< A, B >
	{
		public A getA();

		public B getB();
	}

	public static class ValuePair< A, B > implements Pair< A, B >
	{
		final public A a;

		final public B b;

		public ValuePair( final A a, final B b )
		{
			this.a = a;
			this.b = b;
		}

		@Override
		public A getA()
		{
			return a;
		}

		@Override
		public B getB()
		{
			return b;
		}
	}
}
