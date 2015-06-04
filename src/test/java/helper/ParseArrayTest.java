package helper;

import org.junit.Assert;
import org.junit.Test;
import spim.fiji.plugin.resave.PluginHelper;

/**
 * Created by moon on 6/4/15.
 */
public class ParseArrayTest
{
	@Test
	public void parseInteger()
	{
		final String test = "{1,    5, 2, 3, 4, 5, 612}";

		int[] arr = PluginHelper.parseArrayIntegerString( test );

		for ( int i = 0; i < arr.length; i++ )
			System.out.println(arr[i]);

		Assert.assertEquals( 7, arr.length);
	}

	@Test
	public void parseDouble()
	{
		final String test = "{1.0,    5, 2.34232, 3.112321, 4.353543}";

		double[] arr = PluginHelper.parseArrayDoubleString( test );

		for ( int i = 0; i < arr.length; i++ )
			System.out.println(arr[i]);

		Assert.assertEquals( 5, arr.length);
	}

	@Test
	public void parseBoolean()
	{
		final String test = "{true, false, TRUE, FALSE, True, False}";

		boolean[] arr = PluginHelper.parseArrayBooleanString( test );

		for ( int i = 0; i < arr.length; i++ )
			System.out.println(arr[i]);

		Assert.assertEquals( 6, arr.length);
	}
}
