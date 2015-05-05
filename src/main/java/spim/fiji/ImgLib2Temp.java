package spim.fiji;


public class ImgLib2Temp
{
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
